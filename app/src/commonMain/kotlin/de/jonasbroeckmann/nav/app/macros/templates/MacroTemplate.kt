package de.jonasbroeckmann.nav.app.macros.templates

import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.KnownMacroProperty
import de.jonasbroeckmann.nav.app.macros.components.MacroProperty
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.expressions.TemplatedExpressionString
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Companion.stringify

data class MacroTemplate private constructor(
    val parts: List<Part> = emptyList()
) : MacroEvaluable<String> {
    sealed interface Part : MacroEvaluable<String> {
        val knownUsedProperties: Set<KnownMacroProperty>

        fun tryEvaluateScopeless(): String?
    }

    data class Text(val text: String) : Part {
        override val knownUsedProperties: Set<Nothing> get() = emptySet()

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = text

        override fun tryEvaluateScopeless() = text

        operator fun plus(other: Text) = Text(text + other.text)

        override fun toString() = text
    }

    data class Placeholder(val expressionString: TemplatedExpressionString) : Part {
        constructor(vararg parts: Part) : this(TemplatedExpressionString(MacroTemplate(*parts)))

        constructor(expressionString: String) : this(Text(expressionString))

        constructor(expression: MacroExpression) : this(expression.expressionString.raw)

        constructor(property: MacroProperty<*>) : this(property.expression)

        override val knownUsedProperties get() = expressionString.knownUsedProperties

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = expressionString.evaluate().evaluate().stringify()

        override fun tryEvaluateScopeless() = null

        override fun toString() = "{{$expressionString}}"
    }

    val knownUsedProperties: Set<KnownMacroProperty> by lazy { parts.flatMapTo(mutableSetOf()) { it.knownUsedProperties } }

    val templateString by lazy { TemplateString(parts.joinToString("")) }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = parts.joinToString("") { it.evaluate() }

    fun tryEvaluateScopeless(): String? = parts.map { it.tryEvaluateScopeless() ?: return null }.joinToString("")

    operator fun plus(part: Part): MacroTemplate {
        if (part !is Text) return MacroTemplate(parts + part)
        if (part.text.isEmpty()) return this
        return when (val last = parts.lastOrNull()) {
            is Text -> MacroTemplate(parts.dropLast(1) + (last + part))
            else -> MacroTemplate(parts + part)
        }
    }

    override fun toString() = templateString.raw

    companion object {
        operator fun invoke(vararg parts: Part) = MacroTemplate(parts.asSequence())

        operator fun invoke(parts: Iterable<Part>) = MacroTemplate(parts.asSequence())

        operator fun invoke(parts: Sequence<Part>) = parts
            .fold(MacroTemplate()) { parsed, part -> parsed + part }
    }
}
