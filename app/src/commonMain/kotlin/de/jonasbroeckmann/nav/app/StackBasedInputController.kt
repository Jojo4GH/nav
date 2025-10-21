package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.framework.input.InputController
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.framework.input.InputModeScope
import kotlin.time.Duration

internal class StackBasedInputController(
    private val context: PartialContext,
    private val inputTimeout: Duration,
    private val onInputModeChanged: (InputMode) -> Unit,
    private val onKeyboardEvent: (KeyboardEvent) -> Unit,
    private val onCtrlC: StackBasedInputController.() -> Unit,
) : InputController {
    private data class InputModeStackEntry(val mode: InputMode, val id: Int = nextId++) {
        private companion object {
            private var nextId = 0
        }
    }

    private data object NoInputMode : InputMode(null)

    private val inputModeStack = mutableListOf<InputModeStackEntry>(
        InputModeStackEntry(NoInputMode)
    )

    val currentInputMode get() = inputModeStack.last().mode

    operator fun contains(mode: InputMode) = inputModeStack.any { it.mode == mode }

    override fun enterInputMode(mode: InputMode): InputModeScope {
        context.printlnOnDebug { "Switching input mode $currentInputMode to $mode ..." }
        val stackEntry = InputModeStackEntry(mode)
        inputModeStack.add(stackEntry)
        onInputModeChanged(mode)
        return object : InputModeScope {
            override val inputMode get() = mode

            override fun readInput() = this@StackBasedInputController.readInput()

            override fun close() {
                val topEntry = inputModeStack.lastOrNull()
                require(topEntry == stackEntry) {
                    "Cannot pop input mode stack. Expected $stackEntry but was $topEntry. Current stack: $inputModeStack"
                }
                inputModeStack.removeLast()
                onInputModeChanged(currentInputMode)
                context.printlnOnDebug { "Restored input mode from $mode to $currentInputMode" }
            }
        }
    }

    private fun readInput(): InputEvent {
        context.terminal.enterRawMode().use { rawMode ->
            while (true) {
                val input = try {
                    rawMode.readEvent(inputTimeout)
                } catch (_: RuntimeException) {
                    continue // on timeout try again
                }
                context.printlnOnDebug { "Received input event: $input" }
                if (input is KeyboardEvent) {
                    onKeyboardEvent(input)
                    if (input.isCtrlC) onCtrlC()
                }
                return input
            }
        }
    }
}
