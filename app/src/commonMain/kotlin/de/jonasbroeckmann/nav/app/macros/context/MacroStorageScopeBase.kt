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
    macro: Macro?,
    override val sharedLocalStorage: MutableMacroValueStorage
) : MacroEvaluationScopeBase(fullContext, stateProvider, sessionContext, macro, sharedLocalStorage), MacroStorageScope, StateUpdater by stateUpdater {
    override operator fun set(expression: MacroExpression, value: MacroValue?) {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            property.trySet(value, printOnFail = true)
            return
        }
        when (val type = expression.storageType) {
            null, PrivateLocal -> localStorage[expression.path] = value
            PrivateSession -> sessionContext.sessionStorage[expression.path.asPrivate() ?: return] = value
            PrivatePersistent -> sessionContext.persistentStorage?.set(expression.path.asPrivate() ?: return, value)
            SharedLocal -> sharedLocalStorage[expression.path] = value
            SharedSession -> sessionContext.sessionStorage[expression.path.asShared()] = value
            SharedPersistent -> sessionContext.persistentStorage?.set(expression.path.asShared(), value)
            Property -> warnPropertyUnknown(expression)
            Environment -> sessionContext.environmentStorage[expression.path] = value
            is MacroValueStorageType.Custom -> warnCustomStorageNotSupported(type)
        }
    }
}
