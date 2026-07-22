package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroSymbolScope {
    operator fun get(symbol: MacroSymbol): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        val Empty get() = MacroSymbolScopeBase(context, stateProvider)

        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroSymbolScope.() -> R): R = Empty.block()

        operator fun MacroSymbolScope.get(symbolName: String) = get(MacroSymbol.fromString(symbolName))
    }
}
