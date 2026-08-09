package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ExpressionString(val raw: String) : MacroEvaluable<MacroExpression>, CharSequence by raw {
    fun parsed(): TemplatedExpressionString {
        if (raw.isBlank()) throw ParserException("Expression strings must not be empty")
        return TemplatedExpressionString(TemplateString(raw).parsed())
    }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = parsed().evaluate()

    fun tryEvaluateScopeless() = parsed().tryEvaluateScopeless()

    companion object {
        val Empty = ExpressionString("")
    }
}
