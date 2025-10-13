package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.terminal.warning
import com.kgit2.kommand.exception.KommandException
import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio.Inherit
import com.kgit2.kommand.process.Stdio.Pipe
import de.jonasbroeckmann.nav.Constants.BinaryName
import de.jonasbroeckmann.nav.app.actions.Action
import de.jonasbroeckmann.nav.app.actions.MainActions
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroRuntimeContext
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.state.semantics.updateTextField
import de.jonasbroeckmann.nav.app.ui.WidgetAnimation
import de.jonasbroeckmann.nav.app.ui.buildUI
import de.jonasbroeckmann.nav.app.ui.dialogs.DialogScope
import de.jonasbroeckmann.nav.command.*
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.utils.exitProcess
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.which
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App(
    context: PartialContext,
    override val config: Config
) : MainController, PartialContext by context {
    override val editorCommand by lazy {
        // override editor from command line argument or config or fill in default editor
        context.command.configurationOptions.editor
            ?.also { context.printlnOnDebug { "Using editor from command line argument: $it" } }
            ?: config.editor
            ?: findDefaultEditorCommand()
    }

    override val styles by lazy {
        // override from command line argument or config or fill in based on terminal capabilities
        val useSimpleColors = command.configurationOptions.renderMode.accessibility.simpleColors
            ?: config.accessibility.simpleColors
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256 -> false
                ANSI16, NONE -> true
            }
        config.partialColors filledWith when (useSimpleColors) {
            true -> config.partialColors.simpleTheme.styles
            false -> config.partialColors.theme.styles
        }
    }

    override val accessibilitySimpleColors by lazy {
        command.configurationOptions.renderMode.accessibility.simpleColors
            ?: config.accessibility.simpleColors
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256 -> false
                ANSI16, NONE -> true
            }
    }
    override val accessibilityDecorations by lazy {
        command.configurationOptions.renderMode.accessibility.decorations
            ?: config.accessibility.decorations
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256, ANSI16 -> false
                NONE -> true
            }
    }

    override val identifiedMacros by lazy {
        config.macros
            .mapNotNull { macro -> macro.id?.let { it to macro } }
            .toMap()
    }

    private val actions = MainActions(this)

    private val stateManager = StateManager(
        initial = AppState(
            actions = actions,
            state = State.initial(
                startingDirectory = startingDirectory,
                showHiddenEntries = command.configurationOptions.showHiddenEntries ?: config.showHiddenEntries,
                allMenuActions = { actions.menuActions }
            ),
            dialog = null
        )
    )

    override var state
        get() = stateManager.state.state
        set(value) { stateManager.state = stateManager.state.copy(state = value) }

    private var dialog
        get() = stateManager.state.dialog
        set(value) { stateManager.state = stateManager.state.copy(dialog = value) }

    private data class AppState(
        val actions: MainActions,
        val state: State,
        val dialog: Widget?
    )

    private val ui = WidgetAnimation(terminal)

    fun WidgetAnimation.tryUpdate() = stateManager.consume { state ->
        render(
            widget = buildUI(
                actions = state.actions,
                state = state.state,
                dialog = state.dialog
            )
        )
    }

    override val inputTimeout = config.inputTimeoutMillis.takeIf { it > 0 }?.milliseconds ?: Duration.INFINITE

    fun main(): Nothing = catchAllFatal(
        cleanupOnError = {
            ui.stop()
            terminal.cursor.show()
        }
    ) {
        if (!terminal.terminalInfo.interactive) {
            terminal.danger("Cannot use $BinaryName in a non-interactive terminal")
            exitProcess(1)
        }

        terminal.cursor.hide(showOnExit = false)

        try {
            useInputMode(Normal) {
                ui.tryUpdate()
                captureInputEvents { input ->
                    processInput(input)
                    ui.tryUpdate()
                }
            }
        } catch (_: ExitEvent) {
            printlnOnDebug { "Exiting ..." }
        }

        if (!config.clearOnExit) {
            ui.stop()
        } else {
            ui.clear()
        }

        terminal.cursor.show()

        exitProcess(0)
    }

    @Suppress("detekt:CyclomaticComplexMethod", "detekt:ReturnCount")
    private fun processInput(input: InputEvent) {
        if (input !is KeyboardEvent) return
        if (input.ctrl) {
            printlnOnDebug { "Entering quick macro mode ..." }
            state = state.withInputMode(QuickMacro)
        }
        if (state.inputMode == QuickMacro) {
            for (action in actions.quickMacroModeActions) {
                if (context(state) { action matches input.copy(ctrl = false) }) {
                    action.tryRun(input)
                    state = state.withInputMode(currentInputMode)
                    return
                }
            }
            if (input.key in setOf("Control", "Shift", "Alt")) {
                return
            }
            // no action matched, so we continue as normal
            printlnOnDebug { "Exiting quick macro mode ..." }
            state = state.withInputMode(currentInputMode)
        }
        for (action in actions.normalModeActions) {
            if (context(state) { action matches input }) {
                action.tryRun(input)
                return
            }
        }
        val command = state.command
        if (command != null) {
            input.updateTextField(
                current = command,
                onChange = { newCommand -> state = state.withCommand(newCommand) }
            )
        } else {
            input.updateTextField(
                current = state.filter,
                onChange = { newFilter -> state = state.withFilter(newFilter) }
            )
        }
        return
    }

    private fun <E : InputEvent?> Action<State, E, MainController>.tryRun(input: E) {
        try {
            context(state) { run(input) }
        } catch (e: IOException) {
            val msg = e.message
            when {
                msg == null -> {
                    terminal.danger("An unknown error occurred")
                    terminal.info("If this should be considered a bug, please report it.")
                }
                msg.contains("Permission denied", ignoreCase = true) -> {
                    terminal.danger(msg)
                    terminal.info("Try running $BinaryName with elevated permissions :)")
                }
                else -> {
                    terminal.danger("An unknown error occurred: $msg")
                    terminal.info("If this should be considered a bug, please report it.")
                }
            }
        }
    }

    override fun updateState(updater: State.() -> State) {
        state = updater(state)
    }

    override fun openInEditor(file: Path): Int? {
        val editorCommand = editorCommand ?: run {
            terminal.danger("Could not open file. No editor configured")
            return null
        }
        // if the command is quoted or a single word, we assume it's a single path to the executable
        val fullPath = Regex("\"(.+)\"").matchEntire(editorCommand)?.groupValues[1]
            ?: editorCommand.takeUnless { it.any { c -> c.isWhitespace() } }
        if (fullPath != null) {
            return runCommandFromUI(
                exe = which(fullPath)?.toString() ?: fullPath,
                args = listOf("$file")
            ) {
                stdin(Inherit)
            }?.let {
                if (!it.isSuccess) {
                    terminal.danger("Received exit code ${it.exitCode}")
                }
                it.exitCode
            }
        }
        // otherwise we assume it's a complex command that needs to be interpreted by a shell
        var fileString = "$file"
        if (fileString.any { c -> c.isWhitespace() }) {
            // try to escape spaces in file path
            fileString = "\"$fileString\""
        }
        return runCommandFromUIWithShell(
            command = "$editorCommand $fileString"
        ) {
            stdin(Inherit)
        }?.let {
            if (!it.isSuccess) {
                terminal.danger("Received exit code ${it.exitCode}")
            }
            it.exitCode
        }
    }

    override fun runCommand(
        command: String,
        collectOutput: Boolean,
        collectError: Boolean
    ): MainController.RunCommandResult? = runCommandFromUIWithShell(
        command = command,
        collectOutput = collectOutput,
        collectError = collectError
    )

    override fun runEntryMacro(entryMacro: Config.EntryMacro) {
        val command = context(state) {
            entryMacro.computeCommand(requireNotNull(state.currentItem))
        }
        val result = runCommandFromUIWithShell(command) ?: return
        if (result.isSuccess) {
            when (entryMacro.afterSuccessfulCommand) {
                Config.AfterMacroCommand.DoNothing -> { /* no-op */ }
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> exit(state.directory)
                Config.AfterMacroCommand.ExitAtInitialDirectory -> exit()
            }
        } else {
            when (entryMacro.afterFailedCommand) {
                Config.AfterMacroCommand.DoNothing -> terminal.danger("Received exit code ${result.exitCode}")
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> exit(state.directory)
                Config.AfterMacroCommand.ExitAtInitialDirectory -> exit()
            }
        }
    }

    override fun runMacro(macro: Macro) {
        MacroRuntimeContext.run(macro)
    }

    override fun exit(atDirectory: Path?): Nothing {
        atDirectory?.let {
            printlnOnDebug { "Broadcasting \"$it\" to parent shell ..." }
            CDFile.broadcastChangeDirectory(it)
        }
        throw ExitEvent()
    }

    override fun <R> showDialog(block: DialogScope.() -> R): R {
        val previous = dialog
        try {
            useInputMode(Dialog) {
                val scope = object : DialogScope, InputMode by this {
                    override fun render(widget: Widget) {
                        dialog = widget
                        ui.tryUpdate()
                    }
                }
                return scope.block()
            }
        } finally {
            dialog = previous
        }
    }

    private fun runCommandFromUIWithShell(
        command: String,
        collectOutput: Boolean = false,
        collectError: Boolean = false,
        configuration: Command.() -> Command = { this }
    ): MainController.RunCommandResult? {
        val (exe, args) = when (val shell = shell) {
            null -> {
                terminal.danger("I do not know how to interpret the command without a shell: $command")
                terminal.info("Use --init-help to get more information or use --shell to force a shell.")
                return null
            }
            else -> shell.shell to shell.execCommandArgs(command)
        }
        return runCommandFromUI(
            exe = exe,
            args = args,
            collectOutput = collectOutput,
            collectError = collectError,
            configuration = configuration
        )
    }

    private fun runCommandFromUI(
        exe: String,
        args: List<String>,
        collectOutput: Boolean = false,
        collectError: Boolean = false,
        configuration: Command.() -> Command = { this }
    ): MainController.RunCommandResult? {
        ui.clear() // hide UI before running command
        printlnOnDebug { "Running $exe with args $args" }
        val result = try {
            // run command
            val child = Command(exe)
                .args(args)
                .cwd(state.directory.toString())
                .run { if (collectOutput) stdout(Pipe) else this }
                .run { if (collectError) stderr(Pipe) else this }
                .configuration()
                .spawn()
            printlnOnDebug { "  Child pid: ${child.id()}" }
            val stdout = if (collectOutput) child.bufferedStdout()?.readAll() else null
            val stderr = if (collectError) child.bufferedStderr()?.readAll() else null
            val exitCode = child.wait()
            printlnOnDebug { "  Got exit code: $exitCode" }
            MainController.RunCommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr
            )
        } catch (e: KommandException) {
            dangerThrowable(e, "An error occurred while running $exe with args $args: ${e.message}")
            null
        }
        state = state.updatedEntries() // update in case the command changed something
        return result
    }

    private fun readInput(): InputEvent {
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                val input = try {
                    rawMode.readEvent(inputTimeout)
                } catch (_: RuntimeException) {
                    continue // on timeout try again
                }
                printlnOnDebug { "Received input event: $input" }
                if (input is KeyboardEvent) {
                    state = state.withLastReceivedEvent(input)
                    if (input.isCtrlC) throw ExitEvent()
                }
                return input
            }
        }
    }

    private val inputModeStack = ArrayDeque<InputModeKey>()
    private val currentInputMode: InputModeKey get() = inputModeStack.lastOrNull() ?: InputModeKey.Normal

    override fun enterInputMode(mode: InputModeKey): InputMode {
        printlnOnDebug { "Switching input mode $currentInputMode to $mode ..." }
        inputModeStack.addLast(mode)
        state = state.withInputMode(currentInputMode)
        return object : InputMode {
            override fun readInput() = this@App.readInput()

            override fun close() {
                val top = inputModeStack.lastOrNull()
                require(top == mode) {
                    "Input mode stack corrupted. Expected $mode but was $top. Current stack: $inputModeStack"
                }
                inputModeStack.removeLast()
                state = state.withInputMode(currentInputMode)
                printlnOnDebug { "Restored input mode from $mode to $currentInputMode" }
            }
        }
    }

    private class ExitEvent : Throwable()

    companion object {
        context(context: PartialContext)
        operator fun invoke(config: Config) = App(context, config)

        private val DefaultEditorPrograms = listOf("nano", "nvim", "vim", "vi", "code", "notepad")

        context(context: PartialContext)
        private fun findDefaultEditorCommand(): String? {
            context.printlnOnDebug { "Searching for default editor:" }

            fun checkEnvVar(name: String): String? {
                val value = getEnvironmentVariable(name)?.trim() ?: run {
                    context.printlnOnDebug { $$"  $$$name not set" }
                    return null
                }
                if (value.isBlank()) {
                    context.printlnOnDebug { $$"  $$$name is empty" }
                    return null
                }
                context.printlnOnDebug { $$"  Using value of $$$name: $$value" }
                return value
            }

            fun checkProgram(name: String): String? {
                val path = which(name) ?: run {
                    context.printlnOnDebug { $$"  $$name not found in $PATH" }
                    return null
                }
                context.printlnOnDebug { "  Found $name at $path" }
                return "\"$path\"" // quote path to handle spaces
            }

            return sequence {
                yield(checkEnvVar("EDITOR"))
                yield(checkEnvVar("VISUAL"))
                DefaultEditorPrograms.forEach { name ->
                    yield(checkProgram(name))
                }
            }.filterNotNull().firstOrNull().also {
                if (it == null) {
                    context.terminal.danger("Could not find a default editor")
                    context.terminal.warning(specifyEditorMessage)
                }
            }
        }

        private val specifyEditorMessage: String get() {
            return $$"""Please specify an editor via the --editor CLI option, the config file or the $EDITOR environment variable"""
        }
    }
}
