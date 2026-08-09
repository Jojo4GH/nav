package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression

interface MacroValueStorage {
    operator fun get(path: MacroPathExpression): MacroValue?

    operator fun contains(path: MacroPathExpression): Boolean = get(path) != null
}
