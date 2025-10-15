package de.jonasbroeckmann.nav.framework.action

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.framework.input.InputMode

data class MenuAction<Context, Controller>(
    private val description: Context.() -> String,
    private val style: Context.() -> TextStyle? = { null },
    val selectedStyle: TextStyle? = TextStyles.inverse.style,
    private val hidden: Context.() -> Boolean = { false },
    private val condition: Context.(InputMode?) -> Boolean,
    private val action: context(Controller) Context.() -> Unit
) : Action<Context, Nothing?, Controller> {
    context(context: Context)
    override fun description() = context.description()

    context(context: Context)
    override fun style() = context.style()

    context(context: Context)
    override fun isHidden() = context.hidden()

    context(context: Context)
    override fun matches(input: Nothing?, mode: InputMode?) = isAvailable(mode)

    context(context: Context)
    override fun isAvailable(mode: InputMode?) = context.condition(mode)

    context(context: Context, controller: Controller)
    override fun run(input: Nothing?) = context.action()
}
