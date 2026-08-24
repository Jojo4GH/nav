package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.Logger

class InMemoryMacroValueStorage(
    logger: Logger,
    initial: Map<String, MacroValue> = emptyMap()
) : MutableMacroValueStorageBase(logger) {
    private var value = MacroValue.Dictionary(initial)

    override fun value() = value

    override fun update(updater: MacroValue.Dictionary.() -> MacroValue.Dictionary) {
        value = value.updater()
    }

    operator fun set(key: String, newValue: MacroValue?) {
        if (newValue == null) {
            value -= key
        } else {
            value += (key to newValue)
        }
    }

    fun copy() = InMemoryMacroValueStorage(logger, value)

    companion object {
        context(logger: Logger)
        operator fun invoke(initial: Map<String, MacroValue> = emptyMap()) = InMemoryMacroValueStorage(logger, initial)
    }
}
