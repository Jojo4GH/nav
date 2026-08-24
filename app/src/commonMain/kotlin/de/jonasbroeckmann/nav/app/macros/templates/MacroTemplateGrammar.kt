package de.jonasbroeckmann.nav.app.macros.templates

import de.jonasbroeckmann.nav.app.macros.expressions.TemplatedExpressionString
import me.alllex.parsus.parser.Grammar
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.and
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.or
import me.alllex.parsus.parser.parser
import me.alllex.parsus.parser.unaryMinus
import me.alllex.parsus.parser.zeroOrMore
import me.alllex.parsus.token.literalToken
import me.alllex.parsus.token.regexToken

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
