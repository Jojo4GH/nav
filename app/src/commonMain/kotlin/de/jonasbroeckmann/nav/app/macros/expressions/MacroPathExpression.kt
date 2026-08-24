package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Array
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Dictionary
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Text
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType

data class MacroPathExpression(
    val operators: List<Operator>
) {
    constructor(key: Operator.Key, vararg operators: Operator) : this(listOf(key, *operators))

    constructor(key: String) : this(Operator.Key(key))

    init {
        if (operators.isEmpty()) throw ParserException("Path expression must have at least one operator")
        if (operators.first() !is Key) throw ParserException("Path expression must start with a key")
    }

    val key get() = operators.first() as Operator.Key

    sealed interface Operator {
        fun unparse(base: String?): String

        fun applyTo(value: MacroValue?): MacroValue?

        sealed interface Writable<V : MacroValue> : Operator {
            fun Scope.update(value: MacroValue?, updateNext: (MacroValue?) -> MacroValue?): V
        }

        data class Key(val key: String) : Operator, Writable<Dictionary> {
            override fun unparse(base: String?) = if (base == null) key else "$base.$key"

            override fun applyTo(value: MacroValue?): MacroValue? {
                if (value !is Dictionary) return null
                return value[key]
            }

            override fun Scope.update(value: MacroValue?, updateNext: (MacroValue?) -> MacroValue?) = value
                .expectValueOfTypeOrDefault(Dictionary)
                .updated(key, updateNext)
        }

        data class Index(val index: Int) : Operator, Writable<Array> {
            init {
                if (index < 0) throw ParserException("Index must be non-negative")
            }

            override fun unparse(base: String?) = if (base == null) "[$index]" else "$base[$index]"

            override fun applyTo(value: MacroValue?): MacroValue? {
                if (value !is Array) return null
                return value[index]
            }

            override fun Scope.update(value: MacroValue?, updateNext: (MacroValue?) -> MacroValue?) = value
                .expectValueOfTypeOrDefault(Array)
                .updated(index, updateNext)
        }

        sealed class Function(val name: String) : Operator {
            data object Last : Function("last"), Writable<Array> {
                override fun applyTo(value: MacroValue?): MacroValue? {
                    if (value !is Array) return null
                    return value.lastOrNull()
                }

                override fun Scope.update(value: MacroValue?, updateNext: (MacroValue?) -> MacroValue?): Array {
                    val current = value.expectValueOfTypeOrDefault(Array)
                    if (current.isEmpty()) throwHere("Cannot set last element of empty array")
                    return current.updated(current.lastIndex, updateNext)
                }
            }

            data object Next : Function("next"), Writable<Array> {
                override fun applyTo(value: MacroValue?) = null

                override fun Scope.update(value: MacroValue?, updateNext: (MacroValue?) -> MacroValue?): Array {
                    val current = value.expectValueOfTypeOrDefault(Array)
                    return current.updated(current.size, updateNext)
                }
            }

            data object Keys : Function("keys") {
                override fun applyTo(value: MacroValue?): Array? {
                    if (value !is Dictionary) return null
                    return Array(value.keys.map { Text(it) })
                }
            }

            data object Values : Function("values") {
                override fun applyTo(value: MacroValue?): Array? {
                    if (value !is Dictionary) return null
                    return Array(value.values.toList())
                }
            }

            data object Size : Function("size") {
                override fun applyTo(value: MacroValue?): Text? {
                    if (value !is MacroValue.Collection) return null
                    return Text("${value.size}")
                }
            }

            data object Type : Function("type") {
                override fun applyTo(value: MacroValue?) = value?.type?.name?.let { Text(it) }
            }

            override fun unparse(base: String?) = if (base == null) "$name()" else "$name($base)"

            companion object {
                operator fun invoke(name: String) = when (name) {
                    Last.name -> Last
                    Next.name -> Next
                    Keys.name -> Keys
                    Values.name -> Values
                    Size.name -> Size
                    Type.name -> Type
                    else -> throw ParserException("Unknown function: $name")
                }
            }
        }

        interface Scope : Logger {
            fun throwHere(message: String): Nothing

            fun <T : MacroValue> MacroValue?.expectValueOfTypeOrDefault(type: MacroValue.Type<T>): T
        }
    }

    val unparsed by lazy {
        operators
            .fold(null) { base: String?, operator -> operator.unparse(base) }
            .orEmpty()
            .let { ExpressionString(it) }
    }

    operator fun plus(operator: Operator) = MacroPathExpression(operators + operator)

    override fun toString() = unparsed.raw

    companion object {
        operator fun Operator.Key.plus(path: MacroPathExpression) = MacroPathExpression(listOf(this) + path.operators)
    }
}

interface MacroValueReference {
    data class Root(
        val storageType: MacroValueStorageType? = null,
        val key: String
    ) : MacroValueReference {
        context(scope: MacroEvaluationScope)
        fun get() {
            scope.
        }
    }

    data class DictionaryIndex(

    )
}
