package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.input.isCtrlC
import de.jonasbroeckmann.nav.command.printlnOnDebug
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

fun FullContext.readInput(inputTimeout: Duration = this.inputTimeout): InputEvent {
    terminal.enterRawMode().use { rawMode ->
        while (true) {
            try {
                return rawMode.readEvent(inputTimeout).also {
                    printlnOnDebug { "Received input event: $it" }
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
    if (alt || ctrl) return
    when {
        this == KeyboardEvent("Backspace") -> onChange(current.dropLast(1))
        key.length == 1 -> onChange(current + key)
    }
}
