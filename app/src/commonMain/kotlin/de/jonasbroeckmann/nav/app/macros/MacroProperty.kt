package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger

interface MacroProperty<out T : MacroValue?> : MacroEvaluable<T> {
    val name: String

    val expression: MacroExpression

    val expressionString get() = expression.expressionString

    val templateString: StringWithPlaceholders

    context(_: MacroEvaluationScope)
    fun get(): T

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate(): T = get()

    interface Mutable<T : MacroValue?> : MacroProperty<T> {
        context(_: MacroStorageScope)
        fun set(value: T)
    }

    companion object {
        private abstract class BaseMacroProperty<T : MacroValue?>(override val name: String) : MacroProperty<T> {
            override val expression by lazy {
                MacroExpression(
                    storageType = Property,
                    key = name
                )
            }

            override val templateString by lazy {
                MacroTemplate(MacroTemplate.Placeholder(this)).templateString
            }
        }

        fun <T : MacroValue?> delegated(
            name: String,
            onGet: context(MacroEvaluationScope) () -> T
        ): MacroProperty<T> = object : BaseMacroProperty<T>(name) {
            context(_: MacroEvaluationScope)
            override fun get() = onGet()
        }

        fun <T : MacroValue?> delegated(
            name: String,
            onGet: context(MacroEvaluationScope) () -> T,
            onSet: context(MacroStorageScope) (T) -> Unit
        ): Mutable<T> = object : BaseMacroProperty<T>(name), Mutable<T> {
            context(_: MacroEvaluationScope)
            override fun get() = onGet()

            context(_: MacroStorageScope)
            override fun set(value: T) = onSet(value)
        }

        fun delegatedString(
            name: String,
            onGetString: context(MacroEvaluationScope) () -> String
        ) = delegated(
            name = name,
            onGet = { MacroValue.Text(onGetString()) }
        )

        fun delegatedString(
            name: String,
            onGetString: context(MacroEvaluationScope) () -> String,
            onSetString: context(MacroStorageScope) (String) -> Unit
        ) = delegated(
            name = name,
            onGet = { MacroValue.Text(onGetString()) },
            onSet = { newValue -> onSetString(newValue.value) }
        )

        context(scope: MacroStorageScope)
        fun <T : MacroValue?> MacroProperty<T>.trySet(value: T, printOnFail: Boolean = true) {
            if (this is Mutable) {
                set(value)
            } else {
                if (printOnFail) {
                    scope.terminal.danger("Cannot modify $name as it is not mutable.")
                }
            }
        }
    }
}
