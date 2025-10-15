package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroSymbolScope {
    operator fun get(symbol: MacroSymbol): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroSymbolScope.() -> R): R = MacroSymbolScopeBase(context, stateProvider).block()

        operator fun MacroSymbolScope.get(symbolName: String) = get(MacroSymbol.fromString(symbolName))
    }
}
