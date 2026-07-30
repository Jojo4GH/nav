package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.command.printlnOnDebug

open class MacroSymbolScopeBase(
    private val context: FullContext,
    private val stateProvider: StateProvider,
    private val variables: Map<MacroSymbol.Generic, String> = emptyMap()
) : MacroSymbolScope {
    override operator fun get(symbol: MacroSymbol): String = context(context, stateProvider) {
        when (symbol) {
            is MacroSymbol.EnvironmentVariable -> symbol.get()
            is MacroSymbol.Generic -> DefaultMacroProperty.BySymbol[symbol]?.get() ?: variables[symbol] ?: ""
        }
    }
}

open class MutableMacroSymbolScopeBase(
    private val controller: MainController,
    private val variables: MutableMap<MacroSymbol.Generic, String> = mutableMapOf()
) : MacroSymbolScopeBase(
    context = controller,
    stateProvider = controller,
    variables = variables
), MacroSymbolScope.Mutable {
    override operator fun set(symbol: MacroSymbol, value: String): Unit = context(controller) {
        when (symbol) {
            is MacroSymbol.EnvironmentVariable -> {
                controller.printlnOnDebug { "Setting environment variable $symbol to '$value'" }
                symbol.set(value)
            }
            is MacroSymbol.Generic -> {
                DefaultMacroProperty.BySymbol[symbol]?.let {
                    controller.printlnOnDebug { "Setting property $symbol to '$value'" }
                    it.trySet(value)
                    return
                }
                controller.printlnOnDebug { "Setting variable $symbol to '$value'" }
                variables[symbol] = value
            }
        }
    }
}
