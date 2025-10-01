package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.state.State

sealed interface Action<Event : InputEvent?> {
    val description: State.() -> String?

    context(state: State)
    fun style(): TextStyle?

    fun matches(state: State, input: Event): Boolean

    fun isAvailable(state: State): Boolean

    fun perform(state: State, input: Event): App.Event?
}
