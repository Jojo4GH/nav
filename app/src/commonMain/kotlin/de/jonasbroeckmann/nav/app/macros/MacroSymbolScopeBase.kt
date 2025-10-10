package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider

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
