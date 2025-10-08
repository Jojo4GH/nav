package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.State

sealed interface Action<Event : InputEvent?> {
    context(stateProvider: StateProvider)
    fun description(): String

    context(stateProvider: StateProvider)
    fun style(): TextStyle?

    context(stateProvider: StateProvider)
    fun isHidden(): Boolean

    context(stateProvider: StateProvider)
    fun matches(input: Event): Boolean

    context(stateProvider: StateProvider)
    fun isAvailable(): Boolean

    context(stateProvider: StateProvider)
    fun run(input: Event): AppAction<*>?
}
