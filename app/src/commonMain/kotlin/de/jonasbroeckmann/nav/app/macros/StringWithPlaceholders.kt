package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.MacroSymbolScope.Companion.get
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class StringWithPlaceholders(val raw: String) : MacroEvaluable<String> {
    val placeholders get() = PlaceholderRegex.findAll(raw).map { it.groupValues[1] }
    val symbols get() = placeholders.map { MacroSymbol.fromString(it) }

    context(scope: MacroSymbolScope)
    override fun evaluate() = raw.replace(PlaceholderRegex) { matchResult ->
        scope[matchResult.groupValues[1]]
    }

    override fun toString() = raw

    companion object {
        private val PlaceholderRegex = Regex("""\{\{(.+?)\}\}""")

        fun placeholder(name: String) = StringWithPlaceholders("{{${name}}}")

        val Empty = StringWithPlaceholders("")
    }
}
