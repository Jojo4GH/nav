package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.state.State

data class MenuAction(
    private val description: State.() -> String,
    private val style: State.() -> TextStyle? = { null },
    val selectedStyle: TextStyle? = TextStyles.inverse.style,
    private val hidden: State.() -> Boolean = { false },
    private val condition: State.() -> Boolean,
    private val action: context(MainController) State.() -> Unit
) : Action<State, Nothing?, MainController> {
    context(state: State)
    override fun description() = state.description()

    context(state: State)
    override fun style() = state.style()

    context(state: State)
    override fun isHidden() = state.hidden()

    context(state: State)
    override fun matches(input: Nothing?) = isAvailable()

    context(state: State)
    override fun isAvailable() = state.condition()

    context(state: State, controller: MainController)
    override fun run(input: Nothing?) = state.action()
}
