package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.templates.MacroTemplate
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import me.alllex.parsus.parser.ParseError
import me.alllex.parsus.parser.ParsedValue
import me.alllex.parsus.parser.parseOrNull

data class MacroExpression(
    val storageType: MacroValueStorageType?,
    val path: MacroPathExpression
) : MacroEvaluable<MacroValue?> {
    constructor(
        storageType: MacroValueStorageType?,
        key: MacroPathExpression.Operator.Key,
        vararg operators: MacroPathExpression.Operator
    ) : this(storageType, MacroPathExpression(key, *operators))

    constructor(key: MacroPathExpression.Operator.Key, vararg operators: MacroPathExpression.Operator) : this(null, key, *operators)

    constructor(storageType: MacroValueStorageType?, key: String) : this(storageType, MacroPathExpression.Operator.Key(key))

    constructor(key: String) : this(null, key)

    val expressionString by lazy {
        ExpressionString(listOfNotNull(storageType?.key, path.unparsed).joinToString(":"))
    }

    val templateString by lazy {
        MacroTemplate(MacroTemplate.Placeholder(this)).templateString
    }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = scope[this]

    override fun toString() = templateString.raw

    companion object {
        fun parse(string: String) = when (val result = MacroExpressionGrammar.parse(string)) {
            is ParseError -> throw ParserException(result.describe())
            is ParsedValue<MacroExpression> -> result.value
        }

        fun parseOrNull(string: String) = MacroExpressionGrammar.parseOrNull(string)
    }
}
