package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.setEnvironmentVariable

sealed interface MacroVariable {

    val name: String

    context(_: FullContext, _: StateProvider)
    val value: String

    val placeholder get() = StringWithPlaceholders.placeholder(name)

    sealed interface Mutable : MacroVariable {
        context(_: MacroRuntimeContext)
        fun set(value: String)
    }

    data class FromEnvironment(
        override val name: String
    ) : MacroVariable, Mutable {
        private val envName = name.removePrefix(ENV_PREFIX)

        context(_: FullContext, _: StateProvider)
        override val value get() = getEnvironmentVariable(envName).orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) {
            setEnvironmentVariable(envName, value)
        }
    }

    data class DelegatedImmutable(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> String?,
    ) : MacroVariable {
        context(_: FullContext, _: StateProvider)
        override val value get() = onGet().orEmpty()
    }

    data class DelegatedMutable(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> String?,
        private val onSet: context(MacroRuntimeContext) (String) -> Unit
    ) : MacroVariable, Mutable {
        context(_: FullContext, _: StateProvider)
        override val value get() = onGet().orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) = onSet(value)
    }

    data class Custom(
        override val name: String,
        private var _value: String = ""
    ) : MacroVariable, Mutable {

        context(_: FullContext, _: StateProvider)
        override val value get() = _value

        context(_: MacroRuntimeContext)
        override fun set(value: String) {
            _value = value
        }
    }

    companion object {
        private const val ENV_PREFIX = "env:"

        fun fromName(name: String): MacroVariable = if (name.startsWith(ENV_PREFIX)) {
            FromEnvironment(name)
        } else {
            DefaultMacroVariables.ByName[name]?.variable?.invoke() ?: Custom(name)
        }
    }
}
