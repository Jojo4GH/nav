package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.framework.input.InputMode

abstract class KeyActions<Context, Controller, Category>(vararg validInputModes: InputMode) {
    private val validInputModes = setOf(*validInputModes)

    protected val registered = mutableMapOf<Category, MutableList<KeyAction<Context, Controller>>>()

    protected fun Category.registerKeyAction(action: KeyAction<Context, Controller>): KeyAction<Context, Controller> {
        val registeredForCategory = registered.getOrPut(this) { mutableListOf() }
        val i = registeredForCategory.size
        return action.copy(
            condition = condition@{ inputMode ->
                if (inputMode !in validInputModes) {
                    return@condition false
                }
                if (!action.isAvailable(inputMode)) {
                    return@condition false
                }
                val prioritizedActions = registeredForCategory.asSequence().take(i)
                val hasConflictingKeysWith: (KeyAction<Context, Controller>) -> Boolean = when {
                    action.keys != null -> { other ->
                        other.keys == null || action.keys.any { it in other.keys }
                    }
                    else -> { other ->
                        other.keys == null
                    }
                }
                prioritizedActions.none { prioritized ->
                    hasConflictingKeysWith(prioritized) && prioritized.isAvailable(inputMode)
                }
            }
        ).also {
            registeredForCategory += it
        }
    }

    protected fun Category.registerKeyAction(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { false },
        condition: Context.(InputMode?) -> Boolean,
        action: context(Controller) Context.(KeyboardEvent) -> Unit
    ) = registerKeyAction(
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

    companion object {
        protected fun <Context, Controller> KeyActions<Context, Controller, Unit>.registerKeyAction(
            action: KeyAction<Context, Controller>
        ) = Unit.registerKeyAction(action)

        protected fun <Context, Controller> KeyActions<Context, Controller, Unit>.registerKeyAction(
            vararg keys: KeyboardEvent,
            displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
            description: Context.() -> String = { "" },
            style: Context.() -> TextStyle? = { null },
            hidden: Context.() -> Boolean = { false },
            condition: Context.(InputMode?) -> Boolean,
            action: context(Controller) Context.(KeyboardEvent) -> Unit
        ) = Unit.registerKeyAction(
            keys = keys,
            displayKey = displayKey,
            description = description,
            style = style,
            hidden = hidden,
            condition = condition,
            action = action
        )
    }
}
