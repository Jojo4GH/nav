package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import com.github.ajalt.mordant.terminal.Terminal
import com.kgit2.kommand.process.Command
import de.jonasbroeckmann.nav.CDFile
import de.jonasbroeckmann.nav.Config
import kotlinx.io.files.Path
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class App(
    private val terminal: Terminal,
    private val config: Config,
    startingDirectory: Path,
    debugMode: Boolean
) {
    private var state = State(
        directory = startingDirectory,
        cursor = 0,
        debugMode = debugMode
    )
    private val actions = Actions(config)
    private val animation = MainAnimation(
        terminal = terminal,
        config = config,
        actions = actions
    )

    fun main(): Nothing {
        while (true) {
            animation.update(state) // update once to show initial state
            val event: Event.OutsideUI = mainUILoop()

            when (event) {
                Event.Exit -> break
                is Event.ExitAt -> {
                    CDFile.broadcastChangeDirectory(event.directory)
                    break
                }
                is Event.OpenFile -> {
                    animation.clear() // hide animation before opening editor
                    // open editor
                    val exitCode = openInEditorAndWait(config.editor, event.file)
                    if (exitCode != 0) {
                        terminal.danger("Received exit code $exitCode from ${config.editor}")
                    }
                }
            }
        }

        if (!config.clearOnExit) {
            animation.stop()
        } else {
            animation.clear()
        }

        exitProcess(0)
    }


    private fun mainUILoop(): Event.OutsideUI {
        terminal.rawInputLoop(
            sequenceTimout = config.inputTimeoutMillis.let {
                if (config.inputTimeoutMillis > 0) it.milliseconds else Duration.INFINITE
            }
        ) { inputEvent ->
            val event = inputEvent.process()
            if (event is Event.OutsideUI) return event
            if (event is Event.NewState) {
                state = event.state
                if (inputEvent is KeyboardEvent) state = state.copy(lastReceivedEvent = inputEvent)
                animation.update(state)
            }
        }
    }

    private fun InputEvent.process(): Event? {
        if (this !is KeyboardEvent) return null
        if (isCtrlC) return Event.Exit
        for (action in actions.ordered) {
            if (action.matches(this, state)) return action.action(state, this)
        }
        tryUpdateTextField(state.filter)?.let { return Event.NewState(state.filtered(it)) }
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


        private inline fun Terminal.rawInputLoop(sequenceTimout: Duration, handler: (InputEvent) -> Unit): Nothing {
            enterRawMode().use { rawMode ->
                while (true) {
                    val event = try {
                        rawMode.readEvent(sequenceTimout)
                    } catch (e: RuntimeException) {
                        continue // on timeout try again
                    }
                    handler(event)
                }
            }
        }


        private fun openInEditorAndWait(editor: String, file: Path): Int = Command(editor)
            .args(file.toString())
            .spawn()
            .wait()
    }

    sealed interface Event {
        sealed interface OutsideUI : Event

        data class NewState(val state: State) : Event
        data class OpenFile(val file: Path) : OutsideUI
        data class ExitAt(val directory: Path) : OutsideUI
        data object Exit : OutsideUI
    }
}