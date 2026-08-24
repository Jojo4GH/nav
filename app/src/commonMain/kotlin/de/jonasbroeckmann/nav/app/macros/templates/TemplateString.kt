package de.jonasbroeckmann.nav.app.macros.templates

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.parseToAbsolutePath
import de.jonasbroeckmann.nav.app.macros.parseToAbsolutePathToDirectoryOrNull
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import kotlinx.serialization.Serializable
import me.alllex.parsus.parser.getOrElse
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TemplateString(val raw: String) : MacroEvaluable<String>, CharSequence by raw {
    fun parsed() = MacroTemplateGrammar.parse(raw).getOrElse {
        throw ParserException(it.describe())
    }

    fun knownUsedProperties() = parsed().knownUsedProperties

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = parsed().evaluate()

    override fun toString() = raw

    fun asMacroValueEvaluable() = MacroEvaluable { MacroValue(evaluate()) }

    fun asExpressionString() = ExpressionString(raw)

    companion object {
        val Empty = TemplateString("")

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun TemplateString.evaluateToAbsolutePath() = evaluate().parseToAbsolutePath()

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun TemplateString.evaluateToAbsolutePathToDirectoryOrNull() = evaluate().parseToAbsolutePathToDirectoryOrNull()
    }
}
