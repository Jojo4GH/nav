package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.State

data class KeyAction<Context, Output>(
    val keys: List<KeyboardEvent>,
    private val displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
    private val description: Context.() -> String = { "" },
    private val style: Context.() -> TextStyle? = { null },
    private val hidden: Context.() -> Boolean = { false },
    private val condition: Context.() -> Boolean,
    private val action: Context.(KeyboardEvent) -> Output
) : Action<Context, KeyboardEvent, Output> {
    constructor(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { false },
        condition: Context.() -> Boolean,
        action: Context.(KeyboardEvent) -> Output
    ) : this(
        keys = listOf(*keys),
        displayKey = displayKey,
        description = description,
        style = style,
        hidden = hidden,
        condition = condition,
        action = action
    )

    context(context: Context)
    fun displayKey() = context.displayKey()

    context(context: Context)
    override fun description() = context.description()

    context(context: Context)
    override fun style() = context.style()

    context(context: Context)
    override fun isHidden() = context.hidden()

    context(context: Context)
    override fun matches(input: KeyboardEvent): Boolean {
        return input in keys && isAvailable()
    }

    context(context: Context)
    override fun isAvailable() = context.condition()

    context(context: Context)
    override fun run(input: KeyboardEvent) = context.action(input)

    data class Trigger(val key: KeyboardEvent)
}
