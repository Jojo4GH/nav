package de.jonasbroeckmann.nav.app.macros

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class StringWithPlaceholders(val raw: String) : MacroEvaluable<String> {
    val placeholders get() = PlaceholderRegex.findAll(raw).map { it.groupValues[1] }

    context(scope: MacroVariableScope)
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
