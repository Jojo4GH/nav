package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.state.State

data class MenuAction(
    override val description: State.() -> String?,
    private val style: State.() -> TextStyle? = { null },
    val selectedStyle: TextStyle? = TextStyles.inverse.style,
    private val condition: State.() -> Boolean,
    private val action: State.() -> App.Event?
) : Action<Nothing?> {
    context(state: State)
    override fun style() = state.style()

    override fun matches(state: State, input: Nothing?) = isAvailable(state)

    override fun isAvailable(state: State) = state.condition()

    override fun perform(state: State, input: Nothing?) = state.action()
}
