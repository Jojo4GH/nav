@file:UseSerializers(RegexAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable(with = MacroCondition.Companion::class)
sealed interface MacroCondition : MacroEvaluable<Boolean> {

    val usedSymbols: Set<MacroSymbol>

    @Serializable
    @SerialName("any")
    data class Any(val any: List<MacroCondition>) : MacroCondition {
        override val usedSymbols: Set<MacroSymbol> by lazy { any.flatMapTo(mutableSetOf()) { it.usedSymbols } }

        context(scope: MacroSymbolScope)
        override fun evaluate() = any.any { it.evaluate() }
    }

    @Serializable
    @SerialName("all")
    data class All(val all: List<MacroCondition>) : MacroCondition {
        override val usedSymbols: Set<MacroSymbol> by lazy { all.flatMapTo(mutableSetOf()) { it.usedSymbols } }

        context(scope: MacroSymbolScope)
        override fun evaluate() = all.all { it.evaluate() }
    }

    @Serializable
    @SerialName("not")
    data class Not(val not: MacroCondition) : MacroCondition {
        override val usedSymbols get() = not.usedSymbols

        context(scope: MacroSymbolScope)
        override fun evaluate() = !not.evaluate()
    }

    @Serializable
    @SerialName("equal")
    data class Equal(
        val equal: List<StringWithPlaceholders>,
        val ignoreCase: Boolean = false
    ) : MacroCondition {
        init {
            require(equal.size >= 2) { "${::equal.name} must have at least two elements to compare" }
        }

        override val usedSymbols: Set<MacroSymbol> by lazy { equal.flatMapTo(mutableSetOf()) { it.symbols } }

        context(scope: MacroSymbolScope)
        override fun evaluate(): Boolean {
            val toCompare = equal.map { it.evaluate() }
            return toCompare.all { it.equals(toCompare[0], ignoreCase = ignoreCase) }
        }
    }

    @Serializable
    @SerialName("notEqual")
    data class NotEqual(
        val notEqual: List<StringWithPlaceholders>,
        val ignoreCase: Boolean = false
    ) : MacroCondition by Not(Equal(notEqual, ignoreCase))

    @Serializable
    @SerialName("match")
    data class Match(
        val match: Regex,
        @SerialName("in")
        val value: StringWithPlaceholders
    ) : MacroCondition {
        override val usedSymbols by lazy { value.symbols.toSet() }

        context(scope: MacroSymbolScope)
        override fun evaluate() = match.matches(value.evaluate())
    }

    @Serializable
    @SerialName("empty")
    data class Empty(val empty: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { empty.symbols.toSet() }

        context(scope: MacroSymbolScope)
        override fun evaluate() = empty.evaluate().isEmpty()
    }

    @Serializable
    @SerialName("notEmpty")
    data class NotEmpty(val notEmpty: StringWithPlaceholders) : MacroCondition by Not(Empty(notEmpty))

    @Serializable
    @SerialName("blank")
    data class Blank(val blank: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { blank.symbols.toSet() }

        context(scope: MacroSymbolScope)
        override fun evaluate() = blank.evaluate().isBlank()
    }

    @Serializable
    @SerialName("notBlank")
    data class NotBlank(val notBlank: StringWithPlaceholders) : MacroCondition by Not(Blank(notBlank))

    companion object : YamlContentPolymorphicSerializer<MacroCondition>(MacroCondition::class) {
        override fun selectDeserializer(node: YamlNode) = when (node) {
            is YamlMap -> {
                val serializers = listOf(
                    Any.serializer(),
                    All.serializer(),
                    Not.serializer(),
                    Equal.serializer(),
                    NotEqual.serializer(),
                    Match.serializer(),
                    Empty.serializer(),
                    NotEmpty.serializer(),
                    Blank.serializer(),
                    NotBlank.serializer(),
                )
                serializers.firstOrNull { it.descriptor.serialName in node } ?: throw IllegalArgumentException(
                    "Could not determine type of condition at ${node.path.toHumanReadableString()} " +
                        "(must be one of: ${serializers.map { it.descriptor.serialName }})"
                )
            }
            else -> throw IllegalArgumentException("Unexpected node at ${node.path.toHumanReadableString()}")
        }
    }
}
