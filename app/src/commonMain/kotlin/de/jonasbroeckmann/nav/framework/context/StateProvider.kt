package de.jonasbroeckmann.nav.framework.context

import de.jonasbroeckmann.nav.app.state.State

interface StateProvider {
    val state: State
}

context(provider: StateProvider)
val state get() = provider.state
