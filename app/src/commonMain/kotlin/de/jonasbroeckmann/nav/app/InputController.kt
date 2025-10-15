package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent

interface InputController {
    fun enterInputMode(mode: InputMode): InputModeScope
}

abstract class InputMode(val debugLabel: String?) {
    data object Normal : InputMode("N")

    data object QuickMacro : InputMode("M")
}

interface InputModeScope : AutoCloseable {
    fun readInput(): InputEvent
}

inline fun <R> InputController.useInputMode(mode: InputMode, block: InputModeScope.() -> R) = enterInputMode(mode).use { it.block() }

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
