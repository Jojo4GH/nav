package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider

sealed interface MacroProperty : MacroEvaluable<String>, MacroIdentified {
    context(_: FullContext, _: StateProvider)
    fun get(): String

    context(scope: MacroSymbolScope)
    override fun evaluate() = scope[symbol]

    sealed interface Mutable : MacroProperty {
        context(_: MacroRuntimeContext)
        fun set(value: String)
    }

    data class DelegatedImmutable(
        override val symbol: MacroSymbol.Generic,
        private val onGet: context(FullContext, StateProvider) () -> String?,
    ) : MacroProperty {
        context(_: FullContext, _: StateProvider)
        override fun get() = onGet().orEmpty()
    }

    data class DelegatedMutable(
        override val symbol: MacroSymbol.Generic,
        private val onGet: context(FullContext, StateProvider) () -> String?,
        private val onSet: context(MacroRuntimeContext) (String) -> Unit
    ) : Mutable {
        context(_: FullContext, _: StateProvider)
        override fun get() = onGet().orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) = onSet(value)
    }

    companion object {
        context(context: MacroRuntimeContext)
        fun MacroProperty.trySet(value: String, printOnFail: Boolean = true) {
            if (this is Mutable) {
                set(value)
            } else {
                if (printOnFail) {
                    context.terminal.danger("Cannot modify $symbol as it is not mutable.")
                }
            }
        }
    }
}
