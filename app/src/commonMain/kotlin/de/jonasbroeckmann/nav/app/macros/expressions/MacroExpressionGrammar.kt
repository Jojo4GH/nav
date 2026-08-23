package de.jonasbroeckmann.nav.app.macros.expressions

import de.jonasbroeckmann.nav.app.macros.ParserException
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import me.alllex.parsus.parser.Grammar
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.and
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.maybe
import me.alllex.parsus.parser.or
import me.alllex.parsus.parser.parser
import me.alllex.parsus.parser.unaryMinus
import me.alllex.parsus.parser.zeroOrMore
import me.alllex.parsus.token.regexToken
import kotlin.collections.plus

object MacroExpressionGrammar : Grammar<MacroExpression>() {
    init {
        regexToken("""\s+""", ignored = true)
    }

    private val identifier by regexToken("""[A-Za-z_][A-Za-z0-9_]*""")
    private val number by regexToken("""\d+""") map {
        it.text.toIntOrNull() ?: throw ParserException("Invalid number: ${it.text}")
    }
    private val string by regexToken(""""([^"\\]|\\.)*"""") map {
        val raw = it.text.removeSurrounding("\"")
        buildString {
            val iterator = raw.iterator()
            while (iterator.hasNext()) {
                var c = iterator.next()
                if (c != '\\') {
                    append(c)
                    continue
                }
                if (!iterator.hasNext()) throw ParserException("Unterminated string: \"$raw\"")
                c = iterator.next()
                when (c) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    '"' -> append('"')
                    else -> append(c)
                }
            }
        }
    }
    private val dot by regexToken("""\.""")
    private val leftBracket by regexToken("""\[""")
    private val rightBracket by regexToken("""]""")
    private val leftParentheses by regexToken("""\(""")
    private val rightParentheses by regexToken("""\)""")
    private val colon by regexToken(""":""")

    private val key by identifier map { MacroPathExpression.Operator.Key(it.text) }

    private val propertyAccess by -dot and identifier map { property -> MacroPathExpression.Operator.Key(property.text) }

    private val indexAccess by -leftBracket and number and -rightBracket map { index -> MacroPathExpression.Operator.Index(index) }

    private val propertyOrIndexAccesses by zeroOrMore(propertyAccess or indexAccess)

    private val functionApplication by parser(firstTokens = setOf(identifier)) {
        val name = identifier()
        leftParentheses()
        val argument = macroPathOperators()
        rightParentheses()
        argument + MacroPathExpression.Operator.Function(name.text)
    }

    private val keyOrFunctionApplication by functionApplication or (key map { listOf(it) })

    private val macroPathOperators: Parser<List<MacroPathExpression.Operator>>
        by keyOrFunctionApplication and propertyOrIndexAccesses map { (a, b) -> a + b }

    val macroPathExpression: Parser<MacroPathExpression> by macroPathOperators map { MacroPathExpression(it) }

    private val storageTypePrefix by identifier and -colon map { MacroValueStorageType(it.text) }

    val macroExpression: Parser<MacroExpression> by maybe(storageTypePrefix) and macroPathExpression map { (storageType, path) ->
        MacroExpression(storageType, path)
    }

    override val root: Parser<MacroExpression> get() = macroExpression
}
