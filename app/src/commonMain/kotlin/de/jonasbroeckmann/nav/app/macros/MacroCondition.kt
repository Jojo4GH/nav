@file:UseSerializers(RegexAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import de.jonasbroeckmann.nav.app.macros.MacroSymbol.Companion.get
import de.jonasbroeckmann.nav.app.macros.StringWithPlaceholders.Companion.evaluateAsAbsolutePath
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import de.jonasbroeckmann.nav.utils.exists
import de.jonasbroeckmann.nav.utils.isDirectory
import de.jonasbroeckmann.nav.utils.isRegularFile
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

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace {
            any.withIndex().any { (i, condition) ->
                macroTrace({ MacroTraceElement.ConditionAtIndex(i, condition) }) {
                    condition.evaluate()
                }
            }
        }

        companion object {
            operator fun invoke(vararg conditions: MacroCondition) = Any(listOf(*conditions))
        }
    }

    @Serializable
    @SerialName("all")
    data class All(val all: List<MacroCondition>) : MacroCondition {
        override val usedSymbols: Set<MacroSymbol> by lazy { all.flatMapTo(mutableSetOf()) { it.usedSymbols } }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace {
            all.withIndex().all { (i, condition) ->
                macroTrace({ MacroTraceElement.ConditionAtIndex(i, condition) }) {
                    condition.evaluate()
                }
            }
        }

        companion object {
            operator fun invoke(vararg conditions: MacroCondition) = All(listOf(*conditions))
        }
    }

    @Serializable
    @SerialName("not")
    data class Not(val not: MacroCondition) : MacroCondition {
        override val usedSymbols get() = not.usedSymbols

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { !not.evaluate() }
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

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            val toCompare = equal.map { it.evaluate() }
            return toCompare.all { it.equals(toCompare[0], ignoreCase = ignoreCase) }
        }

        companion object {
            @Suppress("detekt:AnnotationOnSeparateLine")
            operator fun <@Suppress("FINAL_UPPER_BOUND") T : StringWithPlaceholders> invoke(
                vararg equal: T,
                ignoreCase: Boolean = false
            ) = Equal(
                equal = listOf(*equal),
                ignoreCase = ignoreCase
            )
        }
    }

    @Serializable
    @SerialName("notEqual")
    data class NotEqual(
        val notEqual: List<StringWithPlaceholders>,
        val ignoreCase: Boolean = false
    ) : MacroCondition by Not(Equal(notEqual, ignoreCase)) {
        companion object {
            @Suppress("detekt:AnnotationOnSeparateLine")
            operator fun <@Suppress("FINAL_UPPER_BOUND") T : StringWithPlaceholders> invoke(
                vararg notEqual: T,
                ignoreCase: Boolean = false
            ) = NotEqual(
                notEqual = listOf(*notEqual),
                ignoreCase = ignoreCase
            )
        }
    }

    @Serializable
    @SerialName("match")
    data class Match(
        val match: Regex,
        @SerialName("in")
        val value: StringWithPlaceholders,
        val ignoreCase: Boolean = false
    ) : MacroCondition {
        override val usedSymbols by lazy { value.symbols.toSet() }

        private val regex by lazy {
            if (ignoreCase) Regex(match.pattern, match.options + RegexOption.IGNORE_CASE) else match
        }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { regex.matches(value.evaluate()) }
    }

    @Serializable
    @SerialName("empty")
    data class Empty(val empty: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { empty.symbols.toSet() }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { empty.evaluate().isEmpty() }
    }

    @Serializable
    @SerialName("notEmpty")
    data class NotEmpty(val notEmpty: StringWithPlaceholders) : MacroCondition by Not(Empty(notEmpty))

    @Serializable
    @SerialName("blank")
    data class Blank(val blank: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { blank.symbols.toSet() }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { blank.evaluate().isBlank() }
    }

    @Serializable
    @SerialName("notBlank")
    data class NotBlank(val notBlank: StringWithPlaceholders) : MacroCondition by Not(Blank(notBlank))

    @Serializable
    @SerialName("exists")
    data class Exists(val exists: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { exists.symbols.toSet() }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { exists.evaluateAsAbsolutePath().exists() }
    }

    @Serializable
    @SerialName("notExists")
    data class NotExists(val notExists: StringWithPlaceholders) : MacroCondition by Not(Exists(notExists))

    @Serializable
    @SerialName("isDirectory")
    data class IsDirectory(val isDirectory: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { isDirectory.symbols.toSet() }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            when (isDirectory) {
                DefaultMacroProperty.EntryName.placeholder, DefaultMacroProperty.EntryPath.placeholder -> {
                    DefaultMacroProperty.EntryType.symbol.get() == DefaultMacroProperty.EntryType.Value.DIRECTORY
                }
                else -> isDirectory.evaluateAsAbsolutePath().isDirectory
            }
        }
    }

    @Serializable
    @SerialName("isNotDirectory")
    data class IsNotDirectory(val isNotDirectory: StringWithPlaceholders) : MacroCondition by Not(IsDirectory(isNotDirectory))

    @Serializable
    @SerialName("isFile")
    data class IsFile(val isFile: StringWithPlaceholders) : MacroCondition {
        override val usedSymbols by lazy { isFile.symbols.toSet() }

        context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            when (isFile) {
                DefaultMacroProperty.EntryName.placeholder, DefaultMacroProperty.EntryPath.placeholder -> {
                    DefaultMacroProperty.EntryType.symbol.get() == DefaultMacroProperty.EntryType.Value.FILE
                }
                else -> isFile.evaluateAsAbsolutePath().isRegularFile
            }
        }
    }

    @Serializable
    @SerialName("isNotFile")
    data class IsNotFile(val isNotFile: StringWithPlaceholders) : MacroCondition by Not(IsFile(isNotFile))

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
                    Exists.serializer(),
                    NotExists.serializer(),
                    IsDirectory.serializer(),
                    IsNotDirectory.serializer(),
                    IsFile.serializer(),
                    IsNotFile.serializer(),
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
