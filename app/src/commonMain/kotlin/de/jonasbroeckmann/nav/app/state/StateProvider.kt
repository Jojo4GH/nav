package de.jonasbroeckmann.nav.app.state

interface StateProvider {
    val state: State
}

context(provider: StateProvider)
val state get() = provider.state
