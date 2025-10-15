package de.jonasbroeckmann.nav.framework.input

abstract class InputMode(val debugLabel: String?) {
    data object Normal : InputMode("N")
}
