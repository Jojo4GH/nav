package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.framework.input.InputMode

data class KeyAction<Context, Controller>(
    /* If null, any key matches */
    val keys: List<KeyboardEvent>?,
    private val displayKey: Context.() -> KeyboardEvent? = { keys?.firstOrNull() },
    private val description: Context.() -> String = { "" },
    private val style: Context.() -> TextStyle? = { null },
    private val hidden: Context.() -> Boolean = { displayKey() == null && description().isEmpty() },
    private val condition: Context.(InputMode?) -> Boolean,
    private val action: context(Controller) Context.(KeyboardEvent) -> Unit
) : Action<Context, KeyboardEvent, Controller> {
    constructor(
        vararg keys: KeyboardEvent,
        displayKey: Context.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: Context.() -> String = { "" },
        style: Context.() -> TextStyle? = { null },
        hidden: Context.() -> Boolean = { displayKey() == null && description().isEmpty() },
        condition: Context.(InputMode?) -> Boolean,
        action: context(Controller) Context.(KeyboardEvent) -> Unit
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
    override fun matches(input: KeyboardEvent, mode: InputMode?): Boolean {
        if (keys != null && input !in keys) return false
        return isAvailable(mode)
    }

    context(context: Context)
    override fun isAvailable(mode: InputMode?) = context.condition(mode)

    context(context: Context, controller: Controller)
    override fun run(input: KeyboardEvent) = context.action(input)

    // TODO add input mode and use
    data class Trigger(val key: KeyboardEvent)
}
