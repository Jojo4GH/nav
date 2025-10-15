package de.jonasbroeckmann.nav.app

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StateManager<T>(initial: T) : ReadWriteProperty<Any?, T> {
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

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state = value
    }
}
