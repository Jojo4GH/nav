package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.filterKeyboardEvents
import de.jonasbroeckmann.nav.app.readInput

interface StateScope<T> {
    var state: T
}

inline fun <T> FullContext.showSimpleInputAnimation(
    initialState: T,
    onInput: StateScope<T>.(KeyboardEvent) -> Unit,
    clearOnExit: Boolean = true,
    crossinline render: T.() -> Widget
) {
    val animation = terminal.animation<T> { it.render() }

    var state = initialState
    var isStateDirty = true

    val stateScope = object : StateScope<T> {
        override var state: T
            get() = state
            set(value) {
                isStateDirty = value != state
                state = value
            }
    }

    try {
        while (true) {
            if (isStateDirty) animation.update(state)
            isStateDirty = false
            readInput().filterKeyboardEvents {
                stateScope.onInput(this)
            }
        }
    } finally {
        if (clearOnExit) {
            animation.clear()
        } else {
            animation.stop()
        }
    }
}
