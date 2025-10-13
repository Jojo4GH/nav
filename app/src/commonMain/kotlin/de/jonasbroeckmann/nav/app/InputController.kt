package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent

interface InputController {
    fun enterInputMode(mode: InputModeKey): InputMode
}

interface InputMode : AutoCloseable {
    fun readInput(): InputEvent
}

sealed interface InputModeKey {
    data object Normal : InputModeKey

    data object QuickMacro : InputModeKey

    data object Dialog : InputModeKey
}

inline fun <R> InputController.useInputMode(mode: InputModeKey, block: InputMode.() -> R) = enterInputMode(mode).use { it.block() }

inline fun InputMode.captureInputEvents(block: (InputEvent) -> Unit): Nothing {
    while (true) {
        block(readInput())
    }
}

inline fun InputMode.captureKeyboardEvents(block: (KeyboardEvent) -> Unit): Nothing {
    captureInputEvents {
        if (it is KeyboardEvent) {
            block(it)
        }
    }
}
