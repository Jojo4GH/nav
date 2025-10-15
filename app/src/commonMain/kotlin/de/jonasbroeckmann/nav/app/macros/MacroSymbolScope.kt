package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.framework.context.FullContext
import de.jonasbroeckmann.nav.framework.context.StateProvider

interface MacroSymbolScope {
    operator fun get(symbol: MacroSymbol): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroSymbolScope.() -> R): R = MacroSymbolScopeBase(context, stateProvider).block()

        operator fun MacroSymbolScope.get(symbolName: String) = get(MacroSymbol.fromString(symbolName))
    }
}
