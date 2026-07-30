package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.MacroSymbolScope.Companion.get
import de.jonasbroeckmann.nav.command.PartialContext
import kotlinx.serialization.Serializable
import me.alllex.parsus.parser.Grammar
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.or
import me.alllex.parsus.parser.parseOrNull
import me.alllex.parsus.parser.parser
import me.alllex.parsus.parser.zeroOrMore
import me.alllex.parsus.token.literalToken
import me.alllex.parsus.token.regexToken
import kotlin.jvm.JvmInline

// TODO rename to TemplateString
@Serializable
@JvmInline
value class StringWithPlaceholders(val raw: String) : MacroEvaluable<String>, CharSequence by raw {
    val placeholders get() = PlaceholderRegex.findAll(raw).map { it.groupValues[1] }
    val symbols get() = placeholders.map { MacroSymbol(it) }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = raw.replace(PlaceholderRegex) { matchResult ->
        val expression = matchResult.groupValues[1]
        MacroExpression
        scope[matchResult.groupValues[1]]
    }

    override fun toString() = raw

    companion object {
        private val PlaceholderRegex = Regex("""\{\{(.+?)\}\}""")

        fun placeholder(name: String) = StringWithPlaceholders("{{$name}}")

        val Empty = StringWithPlaceholders("")

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePath() = evaluate().parseToAbsolutePath()

        context(_: MacroEvaluationScope, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePathToDirectoryOrNull() = evaluate().parseToAbsolutePathToDirectoryOrNull()
    }
}

data class ParsedTemplateString(
    val parts: List<Part>
) : MacroEvaluable<String> {
    sealed interface Part {
        data class Text(val text: String) : Part {
            override fun toString() = text
        }
        data class Placeholder(val templateString: ParsedTemplateString) : Part, MacroEvaluable<String> {
            context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
            override fun evaluate(): String {
                val raw = templateString.evaluate()
                val value = MacroExpression.parse(raw).evaluate()
                return (value as? MacroValue.Text?)?.value.orEmpty()
            }

            override fun toString() = "{{$templateString}}"
        }
    }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate(): String = parts.joinToString("") { part ->
        when (part) {
            is Part.Text -> part.text
            is Part.Placeholder -> part.evaluate()
        }
    }

    override fun toString() = parts.joinToString("")
}

object TemplateStringGrammar : Grammar<ParsedTemplateString>() {
    val openPlaceholder by literalToken("{{")
    val closePlaceholder by literalToken("}}")
    val escapedLeftBrace by literalToken("""\{""") map { ParsedTemplateString.Part.Text("{") }
    val escapedRightBrace by literalToken("""\}""") map { ParsedTemplateString.Part.Text("}") }
    val text by regexToken(""".+""") map { ParsedTemplateString.Part.Text(it.text) }

    val placeholder by parser {
        openPlaceholder()
        val templateString = root()
        closePlaceholder()
        ParsedTemplateString.Part.Placeholder(templateString)
    }

    val part by text or escapedLeftBrace or escapedRightBrace or placeholder

    override val root: Parser<ParsedTemplateString> by zeroOrMore(part) map {
        ParsedTemplateString(it)
    }

}


@Serializable
@JvmInline
value class ExpressionString(val templateString: StringWithPlaceholders) : MacroEvaluable<MacroExpression>, CharSequence by templateString {

}
