package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.MacroCallable
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue

interface MacroCaller {
    context(_: MacroTraceContext)
    fun call(
        parameters: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>? = emptyList(),
        capture: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>? = emptyList(),
        returnToRoot: Boolean = true,
        callable: MacroCallable
    )
}
