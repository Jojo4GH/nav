package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.state.StateProvider

interface MacroProperty<out T : MacroValue?> : MacroEvaluable<T> {
    val name: String

    context(_: FullContext, _: StateProvider)
    fun get(): T

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate(): T {
        scope.get()
    }

    interface Mutable<T : MacroValue?> : MacroProperty<T> {
        context(_: MacroRuntimeContext)
        fun set(value: T)
    }

    data class DelegatedImmutable<out T : MacroValue?>(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> T,
    ) : MacroProperty<T> {
        context(_: FullContext, _: StateProvider)
        override fun get() = onGet()

        companion object {
            operator fun invoke(
                name: String,
                onGetString: context(FullContext, StateProvider) () -> String?
            ) = DelegatedImmutable(
                name = name,
                onGet = { onGetString()?.let { MacroValue.Text(it) } }
            )
        }
    }

    data class DelegatedMutable<T : MacroValue?>(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> T,
        private val onSet: context(MacroRuntimeContext) (T) -> Unit
    ) : Mutable<T> {

        context(_: FullContext, _: StateProvider)
        override fun get() = onGet()

        context(_: MacroRuntimeContext)
        override fun set(value: T) = onSet(value)

        companion object {
            operator fun invoke(
                name: String,
                onGetString: context(FullContext, StateProvider) () -> String?,
                onSetString: context(MacroRuntimeContext) (String?) -> Unit
            ) = DelegatedMutable(
                name = name,
                onGet = { onGetString()?.let { MacroValue.Text(it) } },
                onSet = { newValue -> onSetString(newValue?.value) }
            )
        }
    }

    companion object {
        context(controller: MainController, scope: MacroSymbolScope)
        fun <T : MacroValue?> MacroProperty<T>.trySet(value: T, printOnFail: Boolean = true) {
            if (this is Mutable) {
                set(value)
            } else {
                if (printOnFail) {
                    controller.terminal.danger("Cannot modify $name as it is not mutable.")
                }
            }
        }
    }
}
