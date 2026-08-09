package de.jonasbroeckmann.nav.app.macros.values

class InMemoryMacroValueStorage(
    initial: Map<String, MacroValue> = emptyMap()
) : MutableMacroValueStorageBase() {
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

    fun copy() = InMemoryMacroValueStorage(value)
}
