package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.terminal.warning
import com.kgit2.kommand.exception.KommandException
import com.kgit2.kommand.process.Command
import de.jonasbroeckmann.nav.Constants.BinaryName
import de.jonasbroeckmann.nav.app.AppAction.Exit
import de.jonasbroeckmann.nav.app.AppAction.NoOp
import de.jonasbroeckmann.nav.app.actions.Action
import de.jonasbroeckmann.nav.app.actions.Actions
import de.jonasbroeckmann.nav.app.macros.MacroRuntimeContext
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.UI
import de.jonasbroeckmann.nav.command.CDFile
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.catchAllFatal
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.printlnOnDebug
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
) : FullContext, PartialContext by context, StateProvider {
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

    private val actions = Actions(this)
    override var state = State.initial(
        startingDirectory = startingDirectory,
        showHiddenEntries = command.configurationOptions.showHiddenEntries ?: config.showHiddenEntries,
        allMenuActions = { actions.menuActions }
    )
        private set
    private val ui = UI(this, actions)

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
            while (true) {
                ui.update()
                val inputEvent = readInput()
                printlnOnDebug { "Received input event: $inputEvent" }
                if (inputEvent is KeyboardEvent) state = state.withLastReceivedEvent(inputEvent)
                inputEvent.process().runIn(this)
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

    private class ExitEvent : Throwable()

    private val inputTimeout = config.inputTimeoutMillis.takeIf { it > 0 }?.milliseconds ?: Duration.INFINITE

    private fun readInput(): InputEvent {
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                try {
                    return rawMode.readEvent(inputTimeout)
                } catch (_: RuntimeException) {
                    continue // on timeout try again
                }
            }
        }
    }

    @Suppress("detekt:CyclomaticComplexMethod", "detekt:ReturnCount")
    private fun InputEvent.process(): AppAction<*> {
        if (this !is KeyboardEvent) return NoOp
        if (isCtrlC) return Exit(null)
        if (ctrl) {
            printlnOnDebug { "Entering quick macro mode ..." }
            state = state.inQuickMacroMode(true)
        }
        if (state.inQuickMacroMode) {
            for (action in actions.quickMacroModeActions) {
                if (!action.matches(this)) continue
                return action.tryRun(this) ?: NoOp
            }
            if (key in setOf("Control", "Shift", "Alt")) {
                return NoOp
            }
            // no action matched, so we continue as normal
            printlnOnDebug { "Exiting quick macro mode ..." }
            state = state.inQuickMacroMode(false)
        }
        for (action in actions.ordered) {
            if (action.matches(this)) {
                return action.tryRun(this) ?: NoOp
            }
        }
        val command = state.command
        if (command != null) {
            tryUpdateTextField(command)?.let { newCommand ->
                return AppAction.UpdateState { withCommand(newCommand) }
            }
        } else {
            tryUpdateTextField(state.filter)?.let { newFilter ->
                return AppAction.UpdateState { withFilter(newFilter) }
            }
        }
        return NoOp
    }

    private fun <E : InputEvent?> Action<E>.tryRun(input: E): AppAction<*>? {
        try {
            return run(input)
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
            return null
        }
    }

    fun perform(@Suppress("unused") action: NoOp) = Unit

    fun perform(action: AppAction.UpdateState) {
        val newState = action.update(state)
        if (debugMode) {
            if (state.currentEntry != newState.currentEntry) {
                terminal.println("New entry: ${newState.currentEntry}")
            }
        }
        state = newState
    }

    fun perform(action: AppAction.OpenFile) = openInEditor(action.file)?.also { exitCode ->
        if (exitCode != 0) {
            terminal.danger("Received exit code $exitCode")
        }
    }

    fun perform(action: AppAction.RunCommand) = runCommandFromUIWithShell(
        command = action.command,
        collectOutput = action.collectOutput,
        collectError = action.collectError
    )

    fun perform(action: AppAction.RunEntryMacro) {
        state = state.inQuickMacroMode(false)
        val command = context(state) {
            action.entryMacro.computeCommand(requireNotNull(state.currentEntry))
        }
        val result = runCommandFromUIWithShell(command) ?: return
        if (result.isSuccess) {
            when (action.entryMacro.afterSuccessfulCommand) {
                Config.AfterMacroCommand.DoNothing -> { /* no-op */ }
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> Exit(state.directory).run()
                Config.AfterMacroCommand.ExitAtInitialDirectory -> Exit(null).run()
            }
        } else {
            when (action.entryMacro.afterFailedCommand) {
                Config.AfterMacroCommand.DoNothing -> terminal.danger("Received exit code ${result.exitCode}")
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> Exit(state.directory).run()
                Config.AfterMacroCommand.ExitAtInitialDirectory -> Exit(null).run()
            }
        }
    }

    fun perform(action: AppAction.RunMacro) {
        MacroRuntimeContext.run(action.macro)
    }

    fun perform(action: Exit): Nothing {
        action.atDirectory?.let {
            printlnOnDebug { "Broadcasting \"$it\" to parent shell ..." }
            CDFile.broadcastChangeDirectory(it)
        }
        throw ExitEvent()
    }

    private fun runCommandFromUIWithShell(
        command: String,
        collectOutput: Boolean = false,
        collectError: Boolean = false,
        configuration: Command.() -> Command = { this }
    ): AppAction.RunCommand.Result? {
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
    ): AppAction.RunCommand.Result? {
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
            AppAction.RunCommand.Result(
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

    private fun openInEditor(file: Path): Int? {
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
            }?.exitCode
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
        }?.exitCode
    }


    companion object {
        context(context: PartialContext)
        operator fun invoke(config: Config) = App(context, config)

        private fun KeyboardEvent.tryUpdateTextField(str: String): String? {
            if (alt || ctrl) return null
            return when {
                this == KeyboardEvent("Backspace") -> str.dropLast(1)
                key.length == 1 -> str + key
                else -> null
            }
        }

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

        context(app: App)
        private fun <R> AppAction<R>.run() = runIn(app)
    }
}
