package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.framework.context.FullContext
import de.jonasbroeckmann.nav.framework.context.StateProvider
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
        override fun set(value: String) {
            setEnvironmentVariable(variableName, value)
        }
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
