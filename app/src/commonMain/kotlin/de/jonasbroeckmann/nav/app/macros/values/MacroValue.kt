package de.jonasbroeckmann.nav.app.macros.values

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
sealed interface MacroValue {
    val description: String

    fun stringify(format: StringificationFormat = Representative): String

    @Serializable
    @JvmInline
    value class Text(val value: String = "") : MacroValue, CharSequence by value {
        override val description get() = "text"

        override fun stringify(format: StringificationFormat) = when (format) {
            Representative -> value
            Json -> "\"$value\""
            Textual -> value
        }
    }

    sealed interface Collection : MacroValue {
        val size: Int
    }

    @Serializable
    @JvmInline
    value class Dictionary(val value: Map<String, MacroValue> = emptyMap()) : Collection, Map<String, MacroValue> by value {
        override val description get() = "dictionary"

        fun updated(key: String, update: (MacroValue?) -> MacroValue?): Dictionary {
            val newValue = update(this[key])
            return if (newValue == null) this - key else this + (key to newValue)
        }

        override fun stringify(format: StringificationFormat) = when (format) {
            Representative -> value.asSequence().joinToString(
                separator = ", ",
                prefix = "{ ",
                postfix = " }"
            ) { "${it.key}: ${it.value.stringify(format)}" }
            Json -> value.asSequence().joinToString(
                separator = ", ",
                prefix = "{ ",
                postfix = " }"
            ) { "\"${it.key}\": ${it.value.stringify(format)}" }
            Textual -> ""
        }

        operator fun plus(pair: Pair<String, MacroValue>) = Dictionary(value + pair)

        operator fun minus(key: String) = Dictionary(value - key)
    }

    @Serializable
    @JvmInline
    value class Array(val value: List<MacroValue?> = emptyList()) : Collection, List<MacroValue?> by value {
        override val description get() = "array"

        fun updated(index: Int, update: (MacroValue?) -> MacroValue?): Array {
            val newValue = update(this.getOrNull(index))
            val untruncated = List(maxOf(index + 1, size)) { i ->
                if (i == index) newValue else this.getOrNull(i)
            }
            return Array(untruncated.dropLastWhile { it == null })
        }

        override fun stringify(format: StringificationFormat) = when (format) {
            Representative -> value.joinToString(
                separator = ", ",
                prefix = "[ ",
                postfix = " ]"
            ) { it?.stringify(format) ?: "" }
            Json -> value.joinToString(
                separator = ", ",
                prefix = "[ ",
                postfix = " ]"
            ) { it?.stringify(format) ?: "null" }
            Textual -> ""
        }
    }

    enum class StringificationFormat {
        Representative,
        Json,
        Textual
    }

    companion object {
        operator fun invoke(value: String) = Text(value)

        operator fun invoke(value: String?) = value?.let { Text(it) }

        fun MacroValue?.stringify(format: StringificationFormat = Representative) = this?.stringify(format) ?: when (format) {
            Representative -> "null"
            Json -> "null"
            Textual -> ""
        }
    }
}
