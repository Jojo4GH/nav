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
import com.kgit2.kommand.process.Stdio
import de.jonasbroeckmann.nav.command.CDFile
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.command.NavCommand.Companion.BinaryName
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.UI
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.exitProcess
import de.jonasbroeckmann.nav.utils.getenv
import de.jonasbroeckmann.nav.utils.which
import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App(
    context: PartialContext,
    override val config: Config
) : FullContext, PartialContext by context {

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

    private val actions = Actions(this)
    private var state = State.initial(
        startingDirectory = startingDirectory,
        allMenuActions = { actions.menuActions }
    )
    private val ui = UI(this, actions)

    fun main(): Nothing {
        if (!terminal.terminalInfo.interactive) {
            terminal.danger("Cannot use $BinaryName in a non-interactive terminal")
            exitProcess(1)
        }

        terminal.cursor.hide(showOnExit = false)

        while (true) {
            ui.update(state)
            val inputEvent = readInput()
            printlnOnDebug { "Received input event: $inputEvent" }
            if (inputEvent is KeyboardEvent) state = state.withLastReceivedEvent(inputEvent)
            val appEvent = inputEvent.process()
            if (appEvent == null) {
                continue
            }
            if (!appEvent.handle()) {
                break
            }
        }

        if (!config.clearOnExit) {
            ui.stop()
        } else {
            ui.clear()
        }

        terminal.cursor.show()

        exitProcess(0)
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun Event.handle(): Boolean = when (this) {
        is Event.NewState -> {
            if (debugMode) {
                if (this@App.state.currentEntry != state.currentEntry) {
                    terminal.println("New entry: ${state.currentEntry}")
                }
            }
            this@App.state = state
            true
        }
        Event.Exit -> false
        is Event.ExitAt -> {
            CDFile.broadcastChangeDirectory(directory)
            false
        }
        is Event.OpenFile -> {
            val exitCode = openInEditor(file)
            if (exitCode != null && exitCode != 0) {
                terminal.danger("Received exit code $exitCode")
            }
            true
        }
        is Event.RunCommand -> {
            state.command?.let { command ->
                val exitCode = runCommandFromUIWithShell(command)
                if (exitCode != null && exitCode != 0) {
                    terminal.danger("Received exit code $exitCode")
                }
                state = state.withCommand(null) // clear command
            }
            true
        }
        is Event.RunMacroCommand -> {
            state = state.inQuickMacroMode(false)
            when (runCommandFromUIWithShell(command)) {
                null -> true
                0 -> eventAfterSuccessfulCommand?.handle() ?: true
                else -> eventAfterFailedCommand?.handle() ?: true
            }
        }
    }

    private fun runCommandFromUIWithShell(
        command: String,
        cwd: Path = state.directory,
        configuration: Command.() -> Command = { this }
    ): Int? {
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
            cwd = cwd,
            configuration = configuration
        )
    }

    private fun runCommandFromUI(
        exe: String,
        args: List<String>,
        cwd: Path = state.directory,
        configuration: Command.() -> Command = { this }
    ): Int? {
        ui.clear() // hide UI before running command
        printlnOnDebug { "Running $exe with args $args" }
        val exitCode = try {
            // run command
            Command(exe)
                .args(args)
                .cwd(cwd.toString())
                .configuration()
                .spawn()
                .wait()
        } catch (e: KommandException) {
            dangerThrowable(e, "An error occurred while running $exe with args $args: ${e.message}")
            null
        }
        state = state.updatedEntries() // update in case the command changed something
        return exitCode
    }

    fun openInEditor(file: Path): Int? {
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
            ) { stdin(Stdio.Inherit) }
        }
        // otherwise we assume it's a complex command that needs to be interpreted by a shell
        var fileString = "$file"
        if (fileString.any { c -> c.isWhitespace() }) {
            // try to escape spaces in file path
            fileString = "\"$fileString\""
        }
        return runCommandFromUIWithShell("$editorCommand $fileString") { stdin(Stdio.Inherit) }
    }

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
    private fun InputEvent.process(): Event? {
        if (this !is KeyboardEvent) return null
        if (isCtrlC) return Event.Exit
        if (ctrl) {
            printlnOnDebug { "Entering quick macro mode ..." }
            state = state.inQuickMacroMode(true)
        }
        if (state.inQuickMacroMode) {
            for (action in actions.quickMacroActions) {
                if (!action.matches(state, this)) continue
                return action.tryPerform(state, this, terminal)
            }
            if (key in setOf("Control", "Shift", "Alt")) {
                return null
            }
            // no action matched, so we continue as normal
            printlnOnDebug { "Exiting quick macro mode ..." }
            state = state.inQuickMacroMode(false)
        }
        for (action in actions.ordered) {
            if (action.matches(state, this)) {
                return action.tryPerform(state, this, terminal)
            }
        }
        val command = state.command
        if (command != null) {
            tryUpdateTextField(command)?.let { newCommand ->
                return Event.NewState(state.withCommand(newCommand))
            }
        } else {
            tryUpdateTextField(state.filter)?.let { newFilter ->
                return Event.NewState(state.withFilter(newFilter))
            }
        }
        return null
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
                val value = getenv(name)?.trim() ?: run {
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

    sealed interface Event {
        data class NewState(val state: State) : Event

        sealed interface OutsideUI : Event

        data class OpenFile(val file: Path) : OutsideUI

        data object RunCommand : OutsideUI

        data class RunMacroCommand(
            val command: String,
            val eventAfterSuccessfulCommand: Event?,
            val eventAfterFailedCommand: Event?
        ) : OutsideUI

        data class ExitAt(val directory: Path) : OutsideUI

        data object Exit : OutsideUI
    }
}
