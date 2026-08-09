package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.ParserException

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
        data class Key(val key: String) : Operator {
            override fun unparse(base: String?) = if (base == null) key else "$base.$key"
        }
        data class Index(val index: Int) : Operator {
            init {
                if (index < 0) throw ParserException("Index must be non-negative")
            }
            override fun unparse(base: String?) = if (base == null) "[$index]" else "$base[$index]"
        }
        sealed class Function(val name: String) : Operator {
            data object Last : Function("last")
            data object Next : Function("next")
            data object Keys : Function("keys")
            data object Values : Function("values")
            data object Size : Function("size")

            override fun unparse(base: String?) = if (base == null) "$name()" else "$name($base)"

            companion object {
                operator fun invoke(name: String) = when (name) {
                    Last.name -> Last
                    Next.name -> Next
                    Keys.name -> Keys
                    Values.name -> Values
                    Size.name -> Size
                    else -> throw ParserException("Unknown function: $name")
                }
            }
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
}
