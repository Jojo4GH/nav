package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider

interface MacroSymbolScope {
    operator fun get(symbol: MacroSymbol): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroSymbolScope.() -> R): R = MacroSymbolScopeBase(context, stateProvider).block()

        operator fun MacroSymbolScope.get(symbolName: String) = get(MacroSymbol.fromString(symbolName))
    }
}

open class MacroSymbolScopeBase(
    context: FullContext,
    stateProvider: StateProvider
) : MacroSymbolScope, FullContext by context, StateProvider by stateProvider {

    protected open val variables = mutableMapOf<MacroSymbol.Generic, String>()

    override operator fun get(symbol: MacroSymbol) = when (symbol) {
        is MacroSymbol.EnvironmentVariable -> symbol.get()
        is MacroSymbol.Generic -> {
            DefaultMacroProperties.BySymbol[symbol]?.let { return it.get() }
            variables[symbol].orEmpty()
        }
    }
}
