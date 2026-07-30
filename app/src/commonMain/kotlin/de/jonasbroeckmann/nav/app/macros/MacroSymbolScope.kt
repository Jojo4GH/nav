package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroSymbolScope {
    operator fun get(symbol: MacroSymbol): String

    interface Mutable : MacroSymbolScope {
        operator fun set(symbol: MacroSymbol, value: String)
    }

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        val Empty get() = MacroSymbolScopeBase(context, stateProvider)

        operator fun MacroSymbolScope.get(symbolName: String) = get(MacroSymbol(symbolName))
    }
}
