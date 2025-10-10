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
    private val context: FullContext,
    private val stateProvider: StateProvider
) : MacroSymbolScope {

    protected open val variables = mutableMapOf<MacroSymbol.Generic, String>()

    override operator fun get(symbol: MacroSymbol) = context(context, stateProvider) {
        when (symbol) {
            is MacroSymbol.EnvironmentVariable -> symbol.get()
            is MacroSymbol.Generic -> {
                DefaultMacroProperties.BySymbol[symbol]?.let { return it.get() }
                variables[symbol].orEmpty()
            }
        }
    }
}
