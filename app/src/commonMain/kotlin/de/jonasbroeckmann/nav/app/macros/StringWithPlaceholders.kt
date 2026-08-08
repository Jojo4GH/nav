package de.jonasbroeckmann.nav.app.macros

import kotlinx.serialization.Serializable
import me.alllex.parsus.parser.Grammar
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.and
import me.alllex.parsus.parser.getOrElse
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.or
import me.alllex.parsus.parser.parser
import me.alllex.parsus.parser.unaryMinus
import me.alllex.parsus.parser.zeroOrMore
import me.alllex.parsus.token.literalToken
import me.alllex.parsus.token.regexToken
import kotlin.jvm.JvmInline

// TODO rename to TemplateString
@Serializable
@JvmInline
value class StringWithPlaceholders(val raw: String) : MacroEvaluable<String>, CharSequence by raw {
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
        val Empty = StringWithPlaceholders("")

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePath() = evaluate().parseToAbsolutePath()

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePathToDirectoryOrNull() = evaluate().parseToAbsolutePathToDirectoryOrNull()
    }
}

data class MacroTemplate private constructor(
    val parts: List<Part> = emptyList()
) : MacroEvaluable<String> {
    sealed interface Part : MacroEvaluable<String> {
        val knownUsedProperties: Set<DefaultMacroProperty>

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

    val knownUsedProperties: Set<DefaultMacroProperty> by lazy { parts.flatMapTo(mutableSetOf()) { it.knownUsedProperties } }

    val templateString by lazy { StringWithPlaceholders(parts.joinToString("")) }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = parts.joinToString("") { it.evaluate() }

    fun tryEvaluateScopeless(): String? = parts.map { it.tryEvaluateScopeless() ?: return null }.joinToString("")

    fun asExpressionString() = TemplatedExpressionString(this)

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

data class TemplatedExpressionString(
    val template: MacroTemplate
) : MacroEvaluable<MacroExpression> {
    val knownUsedProperties: Set<DefaultMacroProperty> by lazy {
        template.knownUsedProperties + setOfNotNull(tryEvaluateScopeless()?.let { DefaultMacroProperty.from(it) })
    }

    val expressionString get() = template.templateString.asExpressionString()

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = MacroExpression.parse(template.evaluate())

    fun tryEvaluateScopeless() = template.tryEvaluateScopeless()?.let { MacroExpression.parse(it) }

    override fun toString() = expressionString.raw
}

object MacroTemplateGrammar : Grammar<MacroTemplate>() {
    private val text by regexToken("""[^{}\\]+""")
    private val leftBrace by literalToken("{")
    private val rightBrace by literalToken("}")
    private val backslash by literalToken("""\""")
    private val escapedLeftBrace by -backslash and -leftBrace
    private val escapedRightBrace by -backslash and -rightBrace
    private val escapedBackslash by -backslash and -backslash

    private val placeholder by parser {
        leftBrace()
        leftBrace()
        val expressionString = templatedExpressionString()
        rightBrace()
        rightBrace()
        MacroTemplate.Placeholder(expressionString)
    }

    private val part by placeholder or
        (escapedLeftBrace map { MacroTemplate.Text("{") }) or
        (escapedRightBrace map { MacroTemplate.Text("}") }) or
        (escapedBackslash map { MacroTemplate.Text("\\") }) or
        (leftBrace map { MacroTemplate.Text("{") }) or
        (rightBrace map { MacroTemplate.Text("}") }) or
        (backslash map { MacroTemplate.Text("\\") }) or
        (text map { MacroTemplate.Text(it.text) })

    private val partInExpressionString by placeholder or
        (escapedLeftBrace map { MacroTemplate.Text("{") }) or
        (escapedRightBrace map { MacroTemplate.Text("}") }) or
        (escapedBackslash map { MacroTemplate.Text("\\") }) or
        (backslash map { MacroTemplate.Text("\\") }) or
        (text map { MacroTemplate.Text(it.text) })

    val template: Parser<MacroTemplate> by zeroOrMore(part) map { MacroTemplate(it) }

    val templatedExpressionString: Parser<TemplatedExpressionString> by zeroOrMore(partInExpressionString) map {
        TemplatedExpressionString(MacroTemplate(it))
    }

    override val root by template
}


@Serializable
@JvmInline
value class ExpressionString(val raw: String) : MacroEvaluable<MacroExpression>, CharSequence by raw {
    fun parsed(): TemplatedExpressionString {
        if (raw.isBlank()) throw ParserException("Expression strings must not be empty")
        return TemplatedExpressionString(StringWithPlaceholders(raw).parsed())
    }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = parsed().evaluate()

    fun tryEvaluateScopeless() = parsed().tryEvaluateScopeless()

    companion object {
        val Empty = ExpressionString("")
    }
}
