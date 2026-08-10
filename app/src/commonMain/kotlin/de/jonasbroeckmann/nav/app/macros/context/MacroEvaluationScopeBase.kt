package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateProvider

open class MacroEvaluationScopeBase(
    protected val sessionContext: MacroSessionContext,
    protected open val sharedLocalStorage: MacroValueStorage
) : MacroEvaluationScope, FullContext by sessionContext, StateProvider by sessionContext {
    protected open val localStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()

    override operator fun get(expression: MacroExpression): MacroValue? {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            return property.get()
        }
        return when (val type = expression.storageType) {
            null, Local -> localStorage[expression.path]
            Session -> TODO()
            Persistent -> TODO()
            SharedLocal -> sharedLocalStorage[expression.path]
            SharedSession -> sessionContext.sessionStorage[expression.path]
            SharedPersistent -> sessionContext.persistentStorage?.get(expression.path)
            Property -> {
                warnPropertyUnknown(expression)
                null
            }
            Environment -> sessionContext.environmentStorage[expression.path]
            is MacroValueStorageType.Custom -> {
                warnCustomStorageNotSupported(type)
                null
            }
        }
    }
}
