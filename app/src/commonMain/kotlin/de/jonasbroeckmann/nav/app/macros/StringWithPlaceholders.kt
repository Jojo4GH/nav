package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.MacroSymbolScope.Companion.get
import de.jonasbroeckmann.nav.command.PartialContext
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class StringWithPlaceholders(val raw: String) : MacroEvaluable<String>, CharSequence by raw {
    val placeholders get() = PlaceholderRegex.findAll(raw).map { it.groupValues[1] }
    val symbols get() = placeholders.map { MacroSymbol.fromString(it) }

    context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
    override fun evaluate() = raw.replace(PlaceholderRegex) { matchResult ->
        scope[matchResult.groupValues[1]]
    }

    override fun toString() = raw

    companion object {
        private val PlaceholderRegex = Regex("""\{\{(.+?)\}\}""")

        fun placeholder(name: String) = StringWithPlaceholders("{{$name}}")

        val Empty = StringWithPlaceholders("")

        context(_: MacroSymbolScope, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePath() = evaluate().parseToAbsolutePath()

        context(_: MacroSymbolScope, _: PartialContext, _: MacroTraceContext)
        fun StringWithPlaceholders.evaluateToAbsolutePathToDirectoryOrNull() = evaluate().parseToAbsolutePathToDirectoryOrNull()
    }
}
