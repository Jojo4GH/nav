package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.components.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.app.state.StateUpdater

open class MacroStorageScopeBase(
    fullContext: FullContext,
    stateProvider: StateProvider,
    stateUpdater: StateUpdater,
    sessionContext: MacroSessionContext,
    macroId: Macro.Id?,
    override val sharedLocalStorage: MutableMacroValueStorage
) : MacroEvaluationScopeBase(
    fullContext = fullContext,
    stateProvider = stateProvider,
    sessionContext = sessionContext,
    macroId = macroId,
    sharedLocalStorage = sharedLocalStorage
), MacroStorageScope, StateUpdater by stateUpdater {
    override operator fun set(expression: MacroExpression, value: MacroValue?) {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            property.trySet(value, printOnFail = true)
            return
        }
        when (val type = expression.storageType) {
            null, PrivateLocal -> localStorage[expression.path] = value
            PrivateSession -> sessionContext.privateSessionStorage(idWarnIfNull() ?: return)[expression.path] = value
            PrivatePersistent -> sessionContext.privatePersistentStorage(idWarnIfNull() ?: return)?.set(expression.path, value)
            SharedLocal -> sharedLocalStorage[expression.path] = value
            SharedSession -> sessionContext.sharedSessionStorage[expression.path] = value
            SharedPersistent -> sessionContext.sharedPersistentStorage?.set(expression.path, value)
            Property -> warnPropertyUnknown(expression)
            Environment -> sessionContext.environmentStorage[expression.path] = value
            is MacroValueStorageType.Custom -> warnCustomStorageNotSupported(type)
        }
    }
}
