package de.jonasbroeckmann.nav.app.state.semantics

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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
