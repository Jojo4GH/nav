package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateProvider

open class MacroEvaluationScopeBase(
    fullContext: FullContext,
    stateProvider: StateProvider,
    protected val sessionContext: MacroSessionContext,
    protected val macroId: Macro.Id?,
    protected open val sharedLocalStorage: MacroValueStorage
) : MacroEvaluationScope, FullContext by fullContext, StateProvider by stateProvider {
    protected open val localStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()

    protected fun mutableStorageForType(type: MacroValueStorageType?): MutableMacroValueStorage? {
        return when (type) {
            null, PrivateLocal -> localStorage
            PrivateSession -> sessionContext.privateSessionStorage(idWarnIfNull() ?: return null)
            PrivatePersistent -> sessionContext.privatePersistentStorage(idWarnIfNull() ?: return null)
            SharedLocal -> sharedLocalStorage
            SharedSession -> sessionContext.sharedSessionStorage
            SharedPersistent -> sessionContext.sharedPersistentStorage
            Property -> null
            Environment -> sessionContext.environmentStorage
            is MacroValueStorageType.Custom -> null
        }
    }

    override fun storageForType(type: MacroValueStorageType?): MacroValueStorage {
        TODO("Not yet implemented")
    }

    protected fun idWarnIfNull(): Macro.Id? {
        if (macroId == null) {
            warning("Macros without an id cannot use private (non-shared) storage")
            return null
        }
        return macroId
    }

    override operator fun get(expression: MacroExpression): MacroValue? {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            return property.get()
        }
        return when (val type = expression.storageType) {
            null, PrivateLocal -> localStorage[expression.path]
            PrivateSession -> sessionContext.privateSessionStorage(idWarnIfNull() ?: return null)[expression.path]
            PrivatePersistent -> sessionContext.privatePersistentStorage(idWarnIfNull() ?: return null)?.get(expression.path)
            SharedLocal -> sharedLocalStorage[expression.path]
            SharedSession -> sessionContext.sharedSessionStorage[expression.path]
            SharedPersistent -> sessionContext.sharedPersistentStorage?.get(expression.path)
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
