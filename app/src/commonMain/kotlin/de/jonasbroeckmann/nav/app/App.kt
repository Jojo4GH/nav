package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.kgit2.kommand.exception.KommandException
import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import de.jonasbroeckmann.nav.CDFile
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.Config.Companion.specifyEditorMessage
import de.jonasbroeckmann.nav.ConfigProvider
import de.jonasbroeckmann.nav.Shell
import de.jonasbroeckmann.nav.utils.exitProcess
import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App(
    private val terminal: Terminal,
    override val config: Config,
    startingDirectory: Path,
    private val initShell: Shell?,
    debugMode: Boolean
) : ConfigProvider {
    private val actions = Actions(config)
    private var state = State(
        directory = startingDirectory,
        cursor = 0,
        debugMode = debugMode,
        allMenuActions = { actions.menuActions }
    )
    private val ui = UI(
        terminal = terminal,
        config = config,
        actions = actions
    )

    fun main(): Nothing {
        terminal.cursor.hide(showOnExit = false)

        while (true) {
            ui.update(state)
            val inputEvent = readInput()
            if (state.debugMode) terminal.println("Received input event: $inputEvent")
            if (inputEvent is KeyboardEvent) state = state.copy(lastReceivedEvent = inputEvent)
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

    private fun Event.handle(): Boolean = when (this) {
        is Event.NewState -> {
            if (state.debugMode) {
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
                val exitCode = runCommandFromUI(command)
                if (exitCode != null && exitCode != 0) {
                    terminal.danger("Received exit code $exitCode")
                }
                state = state.withCommand(null) // clear command
            }
            true
        }
        is Event.RunMacroCommand -> {
            state = state.inQuickMacroMode(false)
            when (runCommandFromUI(command)) {
                null -> true
                0 -> eventAfterSuccessfulCommand?.handle() ?: true
                else -> eventAfterFailedCommand?.handle() ?: true
            }
        }
    }

    private fun runCommandFromUI(
        command: String,
        cwd: Path = state.directory,
        configuration: Command.() -> Command = { this }
    ): Int? {
        ui.clear() // hide UI before running command
        val (exe, args) = when (initShell) {
            null -> {
                val parts = command.split(" ")
                parts.first() to parts.drop(1)
            }
            else -> initShell.shell to initShell.execCommandArgs(command)
        }
        if (state.debugMode) terminal.println("Running $exe with args $args")
        val exitCode = try {
            // run command
            Command(exe)
                .args(args)
                .cwd(cwd.toString())
                .configuration()
                .spawn()
                .wait()
        } catch (e: KommandException) {
            terminal.danger("An error occurred while running $exe with args $args: ${e.message}")
            if (state.debugMode) {
                terminal.danger(e.stackTraceToString())
            }
            null
        }
        state = state.updatedEntries() // update in case the command changed something
        return exitCode
    }

    private fun openInEditor(file: Path): Int? {
        val editor = config.editor ?: run {
            terminal.danger("No editor configured")
            terminal.info(specifyEditorMessage)
            return null
        }
        var fileString = "$file"
        if (" " in fileString) {
            // try to escape spaces in file path
            fileString = "\"$fileString\""
        }
        return runCommandFromUI("$editor $fileString") { stdin(Stdio.Inherit) }
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

    private fun InputEvent.process(): Event? {
        if (this !is KeyboardEvent) return null
        if (isCtrlC) return Event.Exit
        if (ctrl) {
            if (state.debugMode) terminal.println("Entering quick macro mode ...")
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
            if (state.debugMode) terminal.println("Exiting quick macro mode ...")
            state = state.inQuickMacroMode(false)
        }
        for (action in actions.ordered) {
            if (!action.matches(state, this)) continue
            return action.tryPerform(state, this, terminal)
        }
        val command = state.command
        if (command != null) {
            tryUpdateTextField(command)?.let { return Event.NewState(state.withCommand(it)) }
        } else {
            tryUpdateTextField(state.filter)?.let { filter ->
                val filtered = state.filtered(filter)
                if (filtered.filteredItems.size == state.filteredItems.size) {
                    return Event.NewState(filtered)
                }
                return Event.NewState(filtered.withCursorOnFirst {
                    it.path.name.startsWith(filter, ignoreCase = true)
                })
            }
        }
        return null
    }

    companion object {
        private fun KeyboardEvent.tryUpdateTextField(str: String): String? {
            if (alt || ctrl) return null
            return when {
                this == KeyboardEvent("Backspace") -> str.dropLast(1)
                key.length == 1 -> str + key
                else -> null
            }
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
