package de.jonasbroeckmann.nav.app.state

interface StateUpdater {
    fun updateState(updater: State.() -> State)
}

context(stateUpdater: StateUpdater)
fun updateState(updater: State.() -> State) = stateUpdater.updateState(updater)
