package de.jonasbroeckmann.nav.framework.input

interface InputController {
    fun enterInputMode(mode: InputMode): InputModeScope
}

inline fun <R> InputController.useInputMode(mode: InputMode, block: InputModeScope.() -> R) = enterInputMode(mode).use { it.block() }
