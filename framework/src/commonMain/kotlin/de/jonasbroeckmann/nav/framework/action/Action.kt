package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.framework.input.InputMode

sealed interface Action<Context, Input : InputEvent?, Controller> {
    context(context: Context)
    fun description(): String

    context(context: Context)
    fun style(): TextStyle?

    context(context: Context)
    fun isHidden(): Boolean

    context(context: Context)
    fun matches(input: Input, mode: InputMode?): Boolean

    context(context: Context)
    fun isAvailable(mode: InputMode?): Boolean

    context(context: Context)
    fun isShown(mode: InputMode?) = !isHidden() && isAvailable(mode)

    context(context: Context, controller: Controller)
    fun run(input: Input)
}

inline fun <Context, Input : InputEvent?, Controller> Iterable<Action<Context, Input, Controller>>.forEachMatch(
    context: Context,
    input: Input,
    mode: InputMode?,
    onMatch: context(Context) Action<Context, Input, Controller>.() -> Unit
) = context(context) {
    forEach { action ->
        if (action.matches(input, mode)) {
            action.onMatch()
        }
    }
}

context(controller: Controller)
fun <Context, Input : InputEvent?, Controller> Iterable<Action<Context, Input, Controller>>.handle(
    context: Context,
    input: Input,
    mode: InputMode?
): Boolean {
    forEachMatch(context, input, mode) {
        run(input)
        return true
    }
    return false
}
