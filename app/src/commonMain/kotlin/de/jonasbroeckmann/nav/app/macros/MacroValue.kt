package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.setEnvironmentVariable

sealed class MacroSymbol {
    abstract val name: String

    val placeholder by lazy {
        StringWithPlaceholders.placeholder(name)
    }

    override fun toString() = "$placeholder"

    data class Generic(override val name: String) : MacroSymbol()

    data class EnvironmentVariable(
        private val variableName: String
    ) : MacroSymbol(), MacroProperty.Mutable {
        override val name by lazy {
            "$ENV_PREFIX$PREFIX_SEPARATOR$variableName"
        }

        override val symbol get() = this

        context(_: FullContext, _: StateProvider)
        override fun get() = getEnvironmentVariable(variableName).orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) { setEnvironmentVariable(variableName, value) }
    }

    companion object {
        private const val PREFIX_SEPARATOR = ':'
        private const val ENV_PREFIX = "env"

        fun fromString(string: String) = if (string.startsWith("$ENV_PREFIX$PREFIX_SEPARATOR")) {
            EnvironmentVariable(string.removePrefix("$ENV_PREFIX$PREFIX_SEPARATOR"))
        } else {
            Generic(string)
        }
    }
}

sealed interface MacroIdentified {
    val symbol: MacroSymbol
}

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
