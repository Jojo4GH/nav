package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.framework.utils.StateManager
import de.jonasbroeckmann.nav.framework.input.captureKeyboardEvents
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogController
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope

fun <T, R> DialogShowScope.inputDialog(
    initialState: T,
    onInput: DialogController<T, R>.(KeyboardEvent) -> Unit,
    build: T.() -> Widget
): R = DialogControllerImpl<T, R>(initialState).run {
    consumeState { render(build(it)) }
    captureKeyboardEvents { input ->
        onInput(input)
        consumeState { render(build(it)) }
    }
}

private class DialogControllerImpl<T, R>(
    initialState: T
) : DialogController<T, R> {
    private val stateManager = StateManager(initialState)

    override var state by stateManager
        private set

    fun consumeState(onNewState: (T) -> Unit) = stateManager.consume(onNewState)

    override fun updateState(update: T.() -> T) {
        state = state.update()
    }

    override fun dismissDialog(value: R): Nothing = throw DismissEvent(value)

    fun run(block: DialogControllerImpl<T, R>.() -> Nothing): R {
        try {
            block()
        } catch (e: DismissEvent) {
            @Suppress("UNCHECKED_CAST")
            return e.toReturn as R
        }
    }

    private class DismissEvent(val toReturn: Any?) : Throwable()
}
