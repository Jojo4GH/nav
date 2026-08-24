package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
sealed interface MacroValue {
    val type: Type<*>

    fun stringify(format: StringificationFormat = Representative): String

    fun deepEquals(other: MacroValue, ignoreCase: Boolean = false): Boolean

    @Serializable
    @JvmInline
    value class Text(val value: String = "") : MacroValue, CharSequence by value {
        override val type get() = Text

        override fun stringify(format: StringificationFormat) = when (format) {
            Representative -> value
            Json -> "\"$value\""
            Textual -> value
        }

        override fun deepEquals(other: MacroValue, ignoreCase: Boolean): Boolean {
            return other is Text && value.equals(other.value, ignoreCase)
        }

        companion object : Type<Text> {
            override val name = "text"

            override val default = Text()

            override fun safeCast(value: MacroValue) = value as? Text
        }
    }

    sealed interface Collection : MacroValue {
        val size: Int
    }

    @Serializable
    @JvmInline
    value class Dictionary(val value: Map<String, MacroValue> = emptyMap()) : Collection, Map<String, MacroValue> by value {
        constructor(vararg pairs: Pair<String, MacroValue>) : this(mapOf(*pairs))

        override val type get() = Dictionary

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

        override fun deepEquals(other: MacroValue, ignoreCase: Boolean): Boolean {
            if (other !is Dictionary) return false
            if (value.size != other.value.size) return false
            return all { (key, value) ->
                val otherValue = other.get(key, ignoreCase = ignoreCase)
                value.deepEquals(otherValue, ignoreCase = ignoreCase)
            }
        }

        operator fun plus(pair: Pair<String, MacroValue>) = Dictionary(value + pair)

        operator fun minus(key: String) = Dictionary(value - key)

        companion object : Type<Dictionary> {
            override val name = "dictionary"

            override val default = Dictionary()

            override fun safeCast(value: MacroValue) = value as? Dictionary

            private fun <V : Any> Map<String, V>.get(key: String, ignoreCase: Boolean = false): V? {
                if (!ignoreCase) return this[key]
                return this[key] ?: firstNotNullOfOrNull {
                    if (key.equals(it.key, ignoreCase = true)) it.value else null
                }
            }
        }
    }

    @Serializable
    @JvmInline
    value class Array(val value: List<MacroValue?> = emptyList()) : Collection, List<MacroValue?> by value {
        constructor(vararg values: MacroValue?) : this(listOf(*values))

        override val type get() = Array

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

        override fun deepEquals(other: MacroValue, ignoreCase: Boolean): Boolean {
            if (other !is Array) return false
            if (value.size != other.value.size) return false
            forEachIndexed { index, value ->
                if (!value.deepEquals(other[index], ignoreCase = ignoreCase)) return false
            }
            return true
        }

        companion object : Type<Array> {
            override val name = "list"

            override val default = Array()

            override fun safeCast(value: MacroValue) = value as? Array
        }
    }

    sealed interface Type<out T : MacroValue> {
        val name: String

        val default: T

        fun safeCast(value: MacroValue): T?
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

        operator fun Dictionary.get(path: MacroPathExpression) = path.operators.fold<_, MacroValue?>(this) { value, operator ->
            operator.applyTo(value)
        }

        fun MacroValue?.deepEquals(other: MacroValue?, ignoreCase: Boolean = false): Boolean {
            if (this == null && other == null) return true
            if (this == null) return false
            if (other == null) return false
            return deepEquals(other, ignoreCase = ignoreCase)
        }
    }
}
