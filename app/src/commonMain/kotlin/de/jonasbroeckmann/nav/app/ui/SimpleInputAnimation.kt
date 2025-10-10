package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.actions.KeyAction
import de.jonasbroeckmann.nav.app.filterKeyboardEvents
import de.jonasbroeckmann.nav.app.readInput
import de.jonasbroeckmann.nav.command.PartialContext
import kotlin.time.Duration

interface DialogScope<T, in R> {
    var state: T

    fun closeWith(value: R): Nothing
}

private class DialogExitEvent : Throwable()

context(context: PartialContext)
fun <T, R> DialogRenderingScope.dialog(
    initialState: T,
    actions: List<KeyAction<DialogScope<T, R>, Unit>> = emptyList(),
    onUnhandledInput: DialogScope<T, R>.(KeyboardEvent) -> Unit,
    inputTimeout: Duration,
    build: DialogScope<T, R>.(T) -> Widget
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

    try {
        context(scope, Unit) {
            while (true) {
                while (isStateDirty) {
                    isStateDirty = false
                    render(scope.build(state))
                }
                readInput(inputTimeout).filterKeyboardEvents {
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
    }
}

interface DialogController {
    fun <R> showDialog(block: DialogRenderingScope.() -> R): R
}

interface DialogRenderingScope {
    fun render(widget: Widget)
}

fun interface Decorator : (Widget) -> Widget

inline fun <R> DialogRenderingScope.decorate(
    decorator: Decorator,
    block: DialogRenderingScope.() -> R
) = object : DialogRenderingScope {
    override fun render(widget: Widget) = this@decorate.render(decorator(widget))
}.block()
