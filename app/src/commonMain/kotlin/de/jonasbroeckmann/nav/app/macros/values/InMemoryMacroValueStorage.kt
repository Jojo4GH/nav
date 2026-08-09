package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression

class InMemoryMacroValueStorage(
    initial: Map<String, MacroValue> = emptyMap()
) : MutableMacroValueStorageBase() {
    private var value = MacroValue.Dictionary(initial)

    override fun get() = value

    override fun update(updater: MacroValue.() -> MacroValue) {
        val new = value.updater()
        if (new !is MacroValue.Dictionary) throw MacroValueStorageException("Root value must be a dictionary")
        value = new
    }

    operator fun set(key: String, newValue: MacroValue?) {
        if (newValue == null) {
            value -= key
        } else {
            value += (key to newValue)
        }
    }

    fun toMap(): Map<MacroPathExpression, MacroValue> = value.mapKeys { (key, _) -> MacroPathExpression(MacroPathExpression.Operator.Key(key)) }

    fun copy() = InMemoryMacroValueStorage(value)
}
