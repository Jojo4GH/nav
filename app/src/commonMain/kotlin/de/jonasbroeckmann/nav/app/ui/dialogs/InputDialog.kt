package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.actions.KeyAction
import de.jonasbroeckmann.nav.app.computeStateWithKeyboardInput
import de.jonasbroeckmann.nav.command.PartialContext
import kotlin.time.Duration

private class DialogExitEvent(val toReturn: Any?) : Throwable()

typealias DialogKeyAction<T, R> = KeyAction<T, DialogController<T, R>>

context(context: PartialContext)
fun <T, R> DialogRenderingScope.inputDialog(
    initialState: T,
    onInput: DialogController<T, R>.(KeyboardEvent) -> Unit,
    inputTimeout: Duration,
    build: T.() -> Widget
): R {
    try {
        computeStateWithKeyboardInput(
            initialState = initialState,
            onInput = { input, setState ->
                DialogControllerImpl<T, R>(
                    get = { this },
                    set = setState
                ).onInput(input)
            },
            inputTimeout = inputTimeout,
            onNewState = { render(build()) }
        )
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
