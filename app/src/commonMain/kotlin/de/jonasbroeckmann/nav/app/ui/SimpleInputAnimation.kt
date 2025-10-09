package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.KeyAction
import de.jonasbroeckmann.nav.app.filterKeyboardEvents
import de.jonasbroeckmann.nav.app.readInput

interface DialogScope<T, in R> {
    var state: T

    fun closeWith(value: R): Nothing
}

@PublishedApi
internal class DialogExitEvent : Throwable()

inline fun <T, R> FullContext.showDialog(
    initialState: T,
    actions: List<KeyAction<DialogScope<T, R>, Unit>> = emptyList(),
    onUnhandledInput: DialogScope<T, R>.(KeyboardEvent) -> Unit,
    clearOnExit: Boolean = true,
    crossinline render: context(DialogScope<T, R>) T.() -> Widget
): R {

    var state = initialState
    var isStateDirty = true

    var toReturn: R? = null

    val scope = object : DialogScope<T, R> {
        override var state: T
            get() = state
            set(value) {
                isStateDirty = value != state
                state = value
            }

        override fun closeWith(value: R): Nothing {
            toReturn = value
            throw DialogExitEvent()
        }
    }

    val animation = terminal.animation<T> { context(scope) { it.render() } }

    try {
        context(scope) {
            while (true) {
                while (isStateDirty) {
                    isStateDirty = false
                    animation.update(state)
                }
                readInput().filterKeyboardEvents {
                    actions.forEach { action ->
                        if (action matches this) {
                            action.run(this)
                            return@filterKeyboardEvents
                        }
                    }
                    scope.onUnhandledInput(this)
                }
            }
        }
    } catch (_: DialogExitEvent) {
        @Suppress("UNCHECKED_CAST")
        return toReturn as R
    } finally {
        if (clearOnExit) {
            animation.clear()
        } else {
            animation.stop()
        }
    }
}
