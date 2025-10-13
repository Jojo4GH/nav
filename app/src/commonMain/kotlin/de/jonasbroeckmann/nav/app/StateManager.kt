package de.jonasbroeckmann.nav.app

import kotlin.properties.Delegates

class StateManager<T>(initial: T) {
    private var isDirty = true
    var state: T by Delegates.observable(initial) { _, old, new ->
        if (old != new) {
            isDirty = true
        }
    }

    fun consume(onNewState: (T) -> Unit) {
        if (isDirty) {
            isDirty = false
            onNewState(state)
        }
    }
}
