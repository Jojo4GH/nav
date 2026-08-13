package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroEvaluationScope : FullContext, StateProvider {
    operator fun get(expression: MacroExpression): MacroValue?

    companion object {
        context(fullContext: FullContext, stateProvider: StateProvider)
        val Empty: MacroEvaluationScope get() = MacroEvaluationScopeBase(
            fullContext = fullContext,
            stateProvider = stateProvider,
            sessionContext = MacroSessionContext(),
            macro = null,
            sharedLocalStorage = InMemoryMacroValueStorage()
        )
    }
}
