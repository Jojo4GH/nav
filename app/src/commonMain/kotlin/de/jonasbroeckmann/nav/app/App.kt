package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.kgit2.kommand.exception.KommandException
import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio.Inherit
import com.kgit2.kommand.process.Stdio.Pipe
import de.jonasbroeckmann.nav.Constants.BinaryName
import de.jonasbroeckmann.nav.app.actions.MenuActions
import de.jonasbroeckmann.nav.app.actions.NormalModeActions
import de.jonasbroeckmann.nav.app.actions.QuickMacroModeActions
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroRuntimeContext
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.buildUI
import de.jonasbroeckmann.nav.command.*
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.action.Action
import de.jonasbroeckmann.nav.framework.input.*
import de.jonasbroeckmann.nav.framework.input.InputMode.Normal
import de.jonasbroeckmann.nav.framework.ui.WidgetAnimation
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.utils.StateManager
import de.jonasbroeckmann.nav.utils.exitProcess
import de.jonasbroeckmann.nav.utils.which
import kotlinx.io.IOException
import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App private constructor(
    context: PartialContext,
    override val config: Config
) : MainControllerBase(), PartialContext by context {
    private val stateManager = StateManager(
        initial = State.initial(
            startingDirectory = startingDirectory,
            showHiddenEntries = command.configurationOptions.showHiddenEntries ?: config.showHiddenEntries,
            normalModeActions = NormalModeActions(this),
            quickMacroModeActions = QuickMacroModeActions(this),
            menuActions = MenuActions(this)
        )
    )

    override var state by stateManager
        private set

    private val ui = WidgetAnimation(terminal)

    fun WidgetAnimation.tryUpdate() = stateManager.consume { state ->
        render(buildUI(state))
    }

    private val inputController = StackBasedInputController(
        context = this,
        inputTimeout = config.inputTimeoutMillis.takeIf { it > 0 }?.milliseconds ?: Duration.INFINITE,
        onInputModeChanged = { mode -> state = state.withInputMode(mode) },
        onKeyboardEvent = { event -> updateState { withLastReceivedEvent(event) } },
        onCtrlC = { exit() }
    )

    override fun enterInputMode(mode: InputMode): InputModeScope = inputController.enterInputMode(mode)

    private fun execute(block: App.() -> Unit): Nothing = catchAllFatal(
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

        val exitCode = try {
            block()
            0
        } catch (e: ExitEvent) {
            printlnOnDebug { "Exiting ..." }
            e.exitCode
        }

        if (!config.clearOnExit) {
            ui.stop()
        } else {
            ui.clear()
        }

        terminal.cursor.show()

        exitProcess(exitCode)
    }

    fun main(): Nothing = useInputMode(Normal) {
        ui.tryUpdate()
        captureInputEvents { input ->
            processInput(input)
            ui.tryUpdate()
        }
    }

    @Suppress("detekt:CyclomaticComplexMethod", "detekt:ReturnCount")
    private fun processInput(input: InputEvent) {
        if (input !is KeyboardEvent) return
        if (input.ctrl) {
            printlnOnDebug { "Entering quick macro mode ..." }
            updateState { inQuickMacroMode(true) }
        }
        if (state.inQuickMacroMode) {
            val inputWithoutCtrl = input.copy(ctrl = false)
            for (action in state.quickMacroModeActions.all) {
                if (context(state) { action.matches(inputWithoutCtrl, state.inputMode) }) {
                    action.tryRun(input)
                    updateState { inQuickMacroMode(false) }
                    return
                }
            }
            if (input.key in setOf("Control", "Shift", "Alt")) {
                return
            }
            // no action matched, so we continue as normal
            printlnOnDebug { "Exiting quick macro mode ..." }
            updateState { inQuickMacroMode(false) }
        }
        for (action in state.normalModeActions.all) {
            if (context(state) { action.matches(input, state.inputMode) }) {
                action.tryRun(input)
                return
            }
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
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> exit(atDirectory = state.directory)
                Config.AfterMacroCommand.ExitAtInitialDirectory -> exit()
            }
        } else {
            when (entryMacro.afterFailedCommand) {
                Config.AfterMacroCommand.DoNothing -> terminal.danger("Received exit code ${result.exitCode}")
                Config.AfterMacroCommand.ExitAtCurrentDirectory -> exit(atDirectory = state.directory)
                Config.AfterMacroCommand.ExitAtInitialDirectory -> exit()
            }
        }
    }

    override fun runMacro(macro: Macro) {
        MacroRuntimeContext.run(macro)
    }

    override fun exit(exitCode: Int, atDirectory: Path?): Nothing {
        atDirectory?.let {
            printlnOnDebug { "Broadcasting \"$it\" to parent shell ..." }
            CDFile.broadcastChangeDirectory(it)
        }
        throw ExitEvent(exitCode)
    }

    private data object DialogInputMode : InputMode("D")

    override fun <R> showDialog(block: DialogShowScope.() -> R): R {
        val previous = state.dialog
        try {
            useInputMode(DialogInputMode) {
                val scope = object : DialogShowScope, InputModeScope by this {
                    override fun render(widget: Widget) {
                        state = state.withDialog(widget)
                        ui.tryUpdate()
                    }
                }
                return scope.block()
            }
        } finally {
            state = state.withDialog(previous)
            // delay updating UI
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
        updateState { updatedEntries() } // update in case the command changed something
        terminal.cursor.hide(showOnExit = false) // hide cursor in case the command unhid it
        return result
    }

    private class ExitEvent(val exitCode: Int) : Throwable()

    companion object {
        context(context: PartialContext)
        operator fun invoke(config: Config, block: App.() -> Unit): Nothing = App(context, config).execute(block)
    }
}
