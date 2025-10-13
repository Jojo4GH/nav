package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.actions.KeyAction
import de.jonasbroeckmann.nav.app.captureKeyboardEvents
import de.jonasbroeckmann.nav.app.StateManager

private class DialogExitEvent(val toReturn: Any?) : Throwable()

typealias DialogKeyAction<T, R> = KeyAction<T, DialogController<T, R>>

fun <T, R> DialogScope.inputDialog(
    initialState: T,
    onInput: DialogController<T, R>.(KeyboardEvent) -> Unit,
    build: T.() -> Widget
): R {
    try {
        val stateManager = StateManager(initialState)
        var state by stateManager::state
        stateManager.consume { render(build(it)) }
        captureKeyboardEvents { input ->
            DialogControllerImpl<T, R>(
                get = { state },
                set = { state = it }
            ).onInput(input)
            stateManager.consume { render(build(it)) }
        }
    } catch (e: DialogExitEvent) {
        @Suppress("UNCHECKED_CAST")
        return e.toReturn as R
    }
}

private class DialogControllerImpl<T, R>(
    private val get: () -> T,
    private val set: (T) -> Unit
) : DialogController<T, R> {
    override val state get() = get()

    override fun updateState(update: T.() -> T) {
        set(state.update())
    }

    override fun dismissDialog(value: R): Nothing = throw DialogExitEvent(value)
}
