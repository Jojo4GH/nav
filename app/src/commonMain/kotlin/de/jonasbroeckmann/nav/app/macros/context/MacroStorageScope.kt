package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateUpdater

interface MacroStorageScope : MacroEvaluationScope, StateUpdater {
    override fun storageForType(type: MacroValueStorageType?): MutableMacroValueStorage

    operator fun set(expression: MacroExpression, value: MacroValue?)

    companion object {
        context(_: MacroTraceContext)
        operator fun MacroStorageScope.set(expression: ExpressionString, value: MacroValue?) {
            this[expression.evaluate()] = value
        }

        context(_: MacroTraceContext)
        operator fun MacroStorageScope.set(expression: ExpressionString, value: String?) = set(expression, MacroValue(value))
    }
}
