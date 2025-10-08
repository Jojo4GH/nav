package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider

interface MacroVariableScope {
    operator fun get(name: String): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroVariableScope.() -> R): R = MacroVariableScopeBase(context, stateProvider).block()
    }
}

open class MacroVariableScopeBase(
    context: FullContext,
    stateProvider: StateProvider
) : MacroVariableScope, FullContext by context, StateProvider by stateProvider {

    private val variables = mutableMapOf<String, MacroVariable>()

    protected fun variable(name: String) = variables.getOrPut(name) { MacroVariable.fromName(name) }

    override operator fun get(name: String): String = variable(name).value
}
