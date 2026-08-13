package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Companion.plus
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
    protected val macro: Macro?,
    protected open val sharedLocalStorage: MacroValueStorage
) : MacroEvaluationScope, FullContext by fullContext, StateProvider by stateProvider {
    protected open val localStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()

    protected fun MacroPathExpression.asPrivate(): MacroPathExpression? {
        val id = macro?.id
        if (id == null) {
            warning("Macros without an id cannot use private (non-shared) storage")
            return null
        }
        return MacroPathExpression.Operator.Key(id) + this
    }

    protected fun MacroPathExpression.asShared() = MacroPathExpression.Operator.Key("shared") + this

    override operator fun get(expression: MacroExpression): MacroValue? {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            return property.get()
        }
        return when (val type = expression.storageType) {
            null, PrivateLocal -> localStorage[expression.path]
            PrivateSession -> sessionContext.sessionStorage[expression.path.asPrivate() ?: return null]
            PrivatePersistent -> sessionContext.persistentStorage?.get(expression.path.asPrivate() ?: return null)
            SharedLocal -> sharedLocalStorage[expression.path]
            SharedSession -> sessionContext.sessionStorage[expression.path.asShared()]
            SharedPersistent -> sessionContext.persistentStorage?.get(expression.path.asShared())
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
