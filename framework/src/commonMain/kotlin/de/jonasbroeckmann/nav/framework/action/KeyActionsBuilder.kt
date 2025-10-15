package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle

inline fun <Context, Controller> buildKeyActions(
    block: KeyActionsBuilder<Context, Controller, Unit>.() -> Unit
) = KeyActionsBuilder<Context, Controller, Unit>().apply(block).actions(Unit)

class KeyActionsBuilder<Context, Controller, Category> : KeyActions<Context, Controller, Category>() {
    fun Category.register(action: KeyAction<Context, Controller>) = registerKeyAction(action)

    fun Category.register(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { false },
        condition: Context.() -> Boolean,
        action: context(Controller) Context.(KeyboardEvent) -> Unit
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

    fun actions(category: Category) = registered[category].orEmpty()
}

fun <Context, Controller> KeyActionsBuilder<Context, Controller, Unit>.register(
    action: KeyAction<Context, Controller>
) = Unit.register(action)

fun <Context, Controller> KeyActionsBuilder<Context, Controller, Unit>.register(
    vararg keys: KeyboardEvent,
    displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
    description: Context.() -> String = { "" },
    style: Context.() -> TextStyle? = { null },
    hidden: Context.() -> Boolean = { false },
    condition: Context.() -> Boolean,
    action: context(Controller) Context.(KeyboardEvent) -> Unit
) = Unit.register(
    keys = keys,
    displayKey = displayKey,
    description = description,
    style = style,
    hidden = hidden,
    condition = condition,
    action = action
)
