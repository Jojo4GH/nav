package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

context(context: PartialContext)
fun readInput(inputTimeout: Duration): InputEvent {
    context.terminal.enterRawMode().use { rawMode ->
        while (true) {
            try {
                return rawMode.readEvent(inputTimeout).also {
                    context.printlnOnDebug { "Received input event: $it" }
                }
            } catch (_: RuntimeException) {
                continue // on timeout try again
            }
        }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <R> InputEvent.filterKeyboardEvents(
    handleExitEvent: Boolean = true,
    block: KeyboardEvent.() -> R
): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    if (this !is KeyboardEvent) return null
    if (handleExitEvent && isCtrlC) throw App.ExitEvent()
    return block()
}

@OptIn(ExperimentalContracts::class)
inline fun KeyboardEvent.updateTextField(
    current: String,
    onChange: (String) -> Unit
) {
    contract { callsInPlace(onChange, InvocationKind.AT_MOST_ONCE) }
    when (this) {
        KeyboardEvent("Backspace") -> onChange(current.dropLast(1))
        KeyboardEvent("Backspace", ctrl = true) -> {
            val lastChar = current.lastOrNull() ?: return
            onChange(current.dropLastWhile { it wordTypeEquals lastChar })
        }
        else -> {
            var event = this
            if (event.ctrl || event.alt) {
                if (event.ctrl && event.alt) {
                    event = event.copy(ctrl = false, alt = false)
                } else {
                    return
                }
            }
            if (event.key.length == 1) {
                onChange(current + event.key)
            }
        }
    }
}

infix fun Char.wordTypeEquals(other: Char): Boolean = when {
    this.isLetterOrDigit() || this == '_' -> other.isLetterOrDigit() || other == '_'
    this.isWhitespace() -> other.isWhitespace()
    else -> this == other
}

context(context: PartialContext)
inline fun <T> computeStateWithKeyboardInput(
    initialState: T,
    onInput: T.(input: KeyboardEvent, setState: (T) -> Unit) -> Unit,
    inputTimeout: Duration,
    onNewState: T.() -> Unit
): Nothing {
    var isDirty = true
    var state = initialState
    while (true) {
        if (isDirty) {
            isDirty = false
            state.onNewState()
        }
        readInput(inputTimeout).filterKeyboardEvents {
            state.onInput(this) { newState ->
                if (newState != state) {
                    isDirty = true
                }
                state = newState
            }
        }
    }
}
