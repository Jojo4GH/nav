package de.jonasbroeckmann.nav.framework.input

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent

interface InputModeScope : AutoCloseable {
    fun readInput(): InputEvent
}

inline fun InputModeScope.captureInputEvents(block: (InputEvent) -> Unit): Nothing {
    while (true) {
        block(readInput())
    }
}

inline fun InputModeScope.captureKeyboardEvents(block: (KeyboardEvent) -> Unit): Nothing {
    captureInputEvents {
        if (it is KeyboardEvent) {
            block(it)
        }
    }
}
