package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle

inline fun <Context, Output> buildKeyActions(
    block: KeyActionsBuilder<Context, Output>.() -> Unit
) = KeyActionsBuilder<Context, Output>().apply(block).actions

class KeyActionsBuilder<Context, Output> : KeyActions<Context, Nothing, Output>() {
    fun register(action: KeyAction<Context, Output>) = action.registered()

    fun register(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { false },
        condition: Context.() -> Boolean,
        action: Context.(KeyboardEvent) -> Output
    ) = register(
        KeyAction(
            keys = keys,
            displayKey = displayKey,
            description = description,
            style = style,
            hidden = hidden,
            condition = condition,
            action = action
        )
    )

    val actions get() = registered[null].orEmpty()
}
