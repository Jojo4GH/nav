package de.jonasbroeckmann.nav.app.actions

abstract class KeyActions<Context, Category, Output> {
    protected val registered = mutableMapOf<Category?, MutableList<KeyAction<Context, Output>>>()

    protected fun KeyAction<Context, Output>.registered(category: Category? = null): KeyAction<Context,Output> {
        val registeredForCategory = registered.getOrPut(category) { mutableListOf() }
        val i = registeredForCategory.size
        return copy(
            condition = {
                isAvailable() &&
                    registeredForCategory.asSequence().take(i).none { prioritized ->
                        keys.any { it in prioritized.keys } && prioritized.isAvailable()
                    }
            }
        ).also {
            registeredForCategory += it
        }
    }
}
