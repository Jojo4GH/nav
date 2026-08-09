package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.StateUpdater
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.components.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType

open class MacroStorageScopeBase(
    stateUpdater: StateUpdater,
    sessionContext: MacroSessionContext,
) : MacroEvaluationScopeBase(sessionContext), MacroStorageScope, StateUpdater by stateUpdater {
    override operator fun set(expression: MacroExpression, value: MacroValue?) {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            property.trySet(value, printOnFail = true)
            return
        }
        when (val type = expression.storageType) {
            Property -> warnPropertyUnknown(expression)
            null, Local -> localStorage[expression.path] = value
            Session -> sessionContext.sessionStorage[expression.path] = value
            Persistent -> sessionContext.persistentStorage?.set(expression.path, value)
            Environment -> sessionContext.environmentStorage[expression.path] = value
            is MacroValueStorageType.Custom -> warnCustomStorageNotSupported(type)
        }
    }
}