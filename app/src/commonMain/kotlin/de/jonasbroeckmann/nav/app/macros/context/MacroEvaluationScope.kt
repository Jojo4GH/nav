package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroEvaluationScope : FullContext, StateProvider {
    fun storageForType(type: MacroValueStorageType?): MacroValueStorage

    operator fun get(expression: MacroExpression): MacroValue?

    companion object {
        context(fullContext: FullContext, stateProvider: StateProvider)
        fun emptyFor(macro: Macro): MacroEvaluationScope = MacroEvaluationScopeBase(
            fullContext = fullContext,
            stateProvider = stateProvider,
            sessionContext = MacroSessionContext(),
            macroId = macro.id,
            sharedLocalStorage = InMemoryMacroValueStorage()
        )

        context(_: MacroTraceContext)
        operator fun MacroEvaluationScope.get(expression: ExpressionString) = this[expression.evaluate()]
    }
}
