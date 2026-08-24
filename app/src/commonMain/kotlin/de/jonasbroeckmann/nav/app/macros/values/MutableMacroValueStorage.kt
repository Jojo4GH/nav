package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression

interface MutableMacroValueStorage : MacroValueStorage {
    operator fun set(path: MacroPathExpression, newValue: MacroValue?)
}
