package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.StateUpdater
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue

interface MacroStorageScope : MacroEvaluationScope, StateUpdater {
    operator fun set(expression: MacroExpression, value: MacroValue?)

    companion object {
        context(_: MacroTraceContext)
        operator fun MacroStorageScope.set(expression: ExpressionString, value: String?) {
            this[expression.evaluate()] = MacroValue(value)
        }
    }
}
