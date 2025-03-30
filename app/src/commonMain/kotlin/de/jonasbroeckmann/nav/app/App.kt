package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.kgit2.kommand.process.Command
import com.kgit2.kommand.process.Stdio
import de.jonasbroeckmann.nav.CDFile
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.Shell
import de.jonasbroeckmann.nav.utils.exitProcess
import kotlinx.io.files.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App(
    private val terminal: Terminal,
    private val config: Config,
    startingDirectory: Path,
    private val initShell: Shell?,
    debugMode: Boolean
) {
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
        while (true) {
            ui.update(state) // update once to show initial state
            when (val event: Event.OutsideUI = mainUILoop()) {
                Event.Exit -> break
                is Event.ExitAt -> {
                    CDFile.broadcastChangeDirectory(event.directory)
                    break
                }
                is Event.OpenFile -> {
                    ui.clear() // hide UI before opening editor
                    // open editor
                    val exitCode = openInEditorAndWait(config.editor, event.file, state.directory)
                    if (exitCode != 0) {
                        terminal.danger("Received exit code $exitCode from ${config.editor}")
                    }
                }
                is Event.RunCommand -> {
                    ui.clear() // hide UI before running command
                    val command = state.command ?: continue
                    val (exe, args) = when (initShell) {
                        null -> {
                            val parts = command.split(" ")
                            parts.first() to parts.drop(1)
                        }
                        else -> initShell.shell to initShell.execCommandArgs(command)
                    }
                    // run command
                    val exitCode = Command(exe).args(args)
                        .cwd(state.directory.toString())
                        .spawn()
                        .wait()
                    if (exitCode != 0) {
                        terminal.danger("Received exit code $exitCode")
                    }
                    state = state
                        .withCommand(null)  // clear command
                        .updatedEntries()             // update in case the command changed something
                }
            }
        }

        if (!config.clearOnExit) {
            ui.stop()
        } else {
            ui.clear()
        }

        exitProcess(0)
    }

    private fun mainUILoop(): Event.OutsideUI {
        val sequenceTimout = config.inputTimeoutMillis.let {
            if (config.inputTimeoutMillis > 0) it.milliseconds else Duration.INFINITE
        }
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                val inputEvent = try {
                    rawMode.readEvent(sequenceTimout)
                } catch (_: RuntimeException) {
                    continue // on timeout try again
                }
                when (val event = inputEvent.process()) {
                    is Event.OutsideUI -> return event
                    is Event.NewState -> {
                        state = event.state
                        if (inputEvent is KeyboardEvent) state = state.copy(lastReceivedEvent = inputEvent)
                        ui.update(state)
                    }
                    null -> { }
                }
            }
        }
    }

    private fun InputEvent.process(): Event? {
        if (this !is KeyboardEvent) return null
        if (isCtrlC) return Event.Exit
        for (action in actions.ordered) {
            if (!action.matches(state, this)) continue
            return action.tryPerform(state, this, terminal)
        }
        val command = state.command
        if (command != null) {
            tryUpdateTextField(command)?.let { return Event.NewState(state.withCommand(it)) }
        } else {
            tryUpdateTextField(state.filter)?.let { return Event.NewState(state.filtered(it)) }
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

        private fun openInEditorAndWait(editor: String, file: Path, cwd: Path): Int = Command(editor)
            .args(file.toString())
            .cwd(cwd.toString())
            .stdin(Stdio.Inherit)
            .spawn()
            .wait()
    }

    sealed interface Event {
        sealed interface OutsideUI : Event

        data class NewState(val state: State) : Event
        data class OpenFile(val file: Path) : OutsideUI
        data object RunCommand : OutsideUI
        data class ExitAt(val directory: Path) : OutsideUI
        data object Exit : OutsideUI
    }
}
