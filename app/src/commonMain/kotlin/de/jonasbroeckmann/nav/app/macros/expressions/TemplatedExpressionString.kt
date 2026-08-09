package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.templates.MacroTemplate

data class TemplatedExpressionString(
    val template: MacroTemplate
) : MacroEvaluable<MacroExpression> {
    val knownUsedProperties: Set<KnownMacroProperty> by lazy {
        template.knownUsedProperties + setOfNotNull(tryEvaluateScopeless()?.let { KnownMacroProperty.from(it) })
    }

    val expressionString get() = template.templateString.asExpressionString()

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = MacroExpression.parse(template.evaluate())

    fun tryEvaluateScopeless() = template.tryEvaluateScopeless()?.let { MacroExpression.parse(it) }

    override fun toString() = expressionString.raw
}
