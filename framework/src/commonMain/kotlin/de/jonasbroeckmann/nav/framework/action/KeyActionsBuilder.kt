package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogController
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <Context, Controller> buildKeyActions(
    vararg validInputModes: InputMode,
    block: KeyActionsBuilder<Context, Controller, Unit>.() -> Unit
): List<KeyAction<Context, Controller>> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return KeyActionsBuilder<Context, Controller, Unit>(*validInputModes).apply(block).actions(Unit)
}

class KeyActionsBuilder<Context, Controller, Category>(
    vararg validInputModes: InputMode
) : KeyActions<Context, Controller, Category>(*validInputModes) {
    fun Category.register(action: KeyAction<Context, Controller>) = registerKeyAction(action)

    fun Category.register(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { false },
        condition: Context.(InputMode?) -> Boolean,
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
    condition: Context.(InputMode?) -> Boolean,
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

typealias DialogKeyAction<T, R> = KeyAction<T, DialogController<T, R>>

@OptIn(ExperimentalContracts::class)
inline fun <T, R> DialogShowScope.buildDialogKeyActions(
    block: KeyActionsBuilder<T, DialogController<T, R>, Unit>.() -> Unit
): List<DialogKeyAction<T, R>> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return buildKeyActions<T, DialogController<T, R>>(inputMode) { block() }
}
