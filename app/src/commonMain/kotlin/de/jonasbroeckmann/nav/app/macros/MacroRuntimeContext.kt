package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.command.printlnOnDebug
import kotlin.collections.emptyMap
import kotlin.collections.forEach

class MacroRuntimeContext private constructor(
    controller: MainController
) : MacroSymbolScopeBase(controller, controller), MainController by controller {

    operator fun set(symbol: MacroSymbol, value: String): Unit = when (symbol) {
        is MacroSymbol.EnvironmentVariable -> {
            printlnOnDebug { "Setting environment variable $symbol to '$value'" }
            symbol.set(value)
        }
        is MacroSymbol.Generic -> {
            DefaultMacroProperties.BySymbol[symbol]?.let {
                printlnOnDebug { "Setting property $symbol to '$value'" }
                return it.trySet(value)
            }
            printlnOnDebug { "Setting variable $symbol to '$value'" }
            variables[symbol] = value
        }
    }

    fun call(
        parameters: Map<MacroSymbol, MacroEvaluable<String>>? = emptyMap(),
        capture: Map<MacroSymbol, MacroEvaluable<String>>? = emptyMap(),
        returnBarrier: Boolean = true,
        runnable: MacroRunnable
    ) {
        val callContext = MacroRuntimeContext(this)

        val input = parameters
            ?.mapValues { (_, evaluable) -> context(this) { evaluable.evaluate() } }
            ?: this.variables
        input.forEach { (symbol, value) ->
            callContext[symbol] = value
        }

        val returnEvent = interceptReturn {
            context(callContext) { runnable.run() }
        }

        val output = capture
            ?.mapValues { (_, evaluable) -> context(callContext) { evaluable.evaluate() } }
            ?: callContext.variables
        output.forEach { (symbol, value) ->
            this[symbol] = value
        }

        if (!returnBarrier && returnEvent != null) {
            throw returnEvent
        }
    }

    private inline fun interceptReturn(block: () -> Unit) = try {
        block()
        null
    } catch (ret: MacroReturnEvent) {
        ret
    }

    fun doReturn(): Nothing = throw MacroReturnEvent()

    private class MacroReturnEvent : Throwable()

    companion object {
        context(app: App)
        fun run(runnable: MacroRunnable) {
            MacroRuntimeContext(app).call(
                parameters = emptyMap(),
                runnable = runnable
            )
        }

        operator fun MacroRuntimeContext.set(symbolName: String, value: String) = set(MacroSymbol.fromString(symbolName), value)
    }
}
