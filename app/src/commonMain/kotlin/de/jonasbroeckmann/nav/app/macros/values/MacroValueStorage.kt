package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Companion.get

interface MacroValueStorage {
    fun value(): MacroValue.Dictionary

    operator fun get(path: MacroPathExpression): MacroValue? = value()[path]

    operator fun contains(path: MacroPathExpression): Boolean = get(path) != null
}
