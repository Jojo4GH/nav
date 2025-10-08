package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.State

data class MenuAction(
    private val description: State.() -> String?,
    private val style: State.() -> TextStyle? = { null },
    val selectedStyle: TextStyle? = TextStyles.inverse.style,
    private val condition: State.() -> Boolean,
    private val action: State.() -> AppAction<*>?
) : Action<Nothing?> {
    context(stateProvider: StateProvider)
    override fun description() = state.description()

    context(stateProvider: StateProvider)
    override fun style() = state.style()

    context(stateProvider: StateProvider)
    override fun matches(input: Nothing?) = isAvailable()

    context(stateProvider: StateProvider)
    override fun isAvailable() = state.condition()

    context(stateProvider: StateProvider)
    override fun run(input: Nothing?) = state.action()
}
