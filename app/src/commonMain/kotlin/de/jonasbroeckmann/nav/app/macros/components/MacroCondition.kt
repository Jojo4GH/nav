@file:UseSerializers(RegexAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros.components

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.MacroTraceElement
import de.jonasbroeckmann.nav.app.macros.contains
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope.Companion.get
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.macroTrace
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString.Companion.evaluateToAbsolutePath
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.macros.values.MacroValue.Companion.deepEquals
import de.jonasbroeckmann.nav.framework.utils.exists
import de.jonasbroeckmann.nav.framework.utils.isDirectory
import de.jonasbroeckmann.nav.framework.utils.isRegularFile
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.collections.plus

@Serializable(with = MacroCondition.Companion::class)
sealed interface MacroCondition : MacroEvaluable<Boolean> {
    val knownUsedProperties: Set<MacroProperty<*>>

    @Serializable
    @SerialName("any")
    data class Any(val any: List<MacroCondition>) : MacroCondition {
        override val knownUsedProperties: Set<MacroProperty<*>> by lazy { any.flatMapTo(mutableSetOf()) { it.knownUsedProperties } }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
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
        override val knownUsedProperties: Set<MacroProperty<*>> by lazy { all.flatMapTo(mutableSetOf()) { it.knownUsedProperties } }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
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
        override val knownUsedProperties get() = not.knownUsedProperties

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { !not.evaluate() }
    }

    @Serializable
    @SerialName("equal")
    data class Equal(
        val equal: List<TemplateString>,
        val ignoreCase: Boolean = false,
        val expressions: Boolean = false
    ) : MacroCondition {
        init {
            require(equal.size >= 2) { "${::equal.name} must have at least two elements to compare" }
        }

        override val knownUsedProperties: Set<MacroProperty<*>> by lazy { equal.flatMapTo(mutableSetOf()) { it.knownUsedProperties() } }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            if (expressions) {
                val toCompare = equal.map { scope[it.asExpressionString()] }
                return toCompare.all { it.deepEquals(toCompare[0], ignoreCase = ignoreCase) }
            } else {
                val toCompare = equal.map { it.evaluate() }
                return toCompare.all { it.equals(toCompare[0], ignoreCase = ignoreCase) }
            }
        }

        companion object {
            @Suppress("detekt:AnnotationOnSeparateLine")
            operator fun <@Suppress("FINAL_UPPER_BOUND") T : TemplateString> invoke(
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
        val notEqual: List<TemplateString>,
        val ignoreCase: Boolean = false
    ) : MacroCondition by Not(Equal(notEqual, ignoreCase)) {
        companion object {
            @Suppress("detekt:AnnotationOnSeparateLine")
            operator fun <@Suppress("FINAL_UPPER_BOUND") T : TemplateString> invoke(
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
        val value: TemplateString,
        val ignoreCase: Boolean = false
    ) : MacroCondition {
        override val knownUsedProperties by lazy { value.knownUsedProperties() }

        private val regex by lazy {
            if (ignoreCase) Regex(match.pattern, match.options + RegexOption.IGNORE_CASE) else match
        }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { regex.matches(value.evaluate()) }
    }

    @Serializable
    @SerialName("empty")
    data class Empty(
        val empty: TemplateString,
        val expression: Boolean = false
    ) : MacroCondition {
        override val knownUsedProperties by lazy { empty.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace {
            if (expression) {
                when (val value = scope[empty.asExpressionString()]) {
                    is MacroValue.Collection -> value.size == 0
                    is MacroValue.Text -> value.isEmpty()
                    null -> true
                }
            } else {
                empty.evaluate().isEmpty()
            }
        }
    }

    @Serializable
    @SerialName("notEmpty")
    data class NotEmpty(val notEmpty: TemplateString) : MacroCondition by Not(Empty(notEmpty))

    @Serializable
    @SerialName("blank")
    data class Blank(val blank: TemplateString) : MacroCondition {
        override val knownUsedProperties by lazy { blank.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { blank.evaluate().isBlank() }
    }

    @Serializable
    @SerialName("notBlank")
    data class NotBlank(val notBlank: TemplateString) : MacroCondition by Not(Blank(notBlank))

    @Serializable
    @SerialName("set")
    data class IsSet(val set: ExpressionString) : MacroCondition {
        override val knownUsedProperties by lazy { set.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { scope[set] != null }
    }

    @Serializable
    @SerialName("notSet")
    data class IsNotSet(val notSet: ExpressionString) : MacroCondition by Not(IsSet(notSet))

    @Serializable
    @SerialName("contains")
    data class Contains(
        val contains: ExpressionString,
        val text: TemplateString? = null,
        val regex: Regex? = null,
        val ignoreCase: Boolean = false
    ) : MacroCondition {
        override val knownUsedProperties by lazy {
            contains.knownUsedProperties() + text?.knownUsedProperties().orEmpty()
        }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace {
            val value = scope[contains] as? MacroValue.Collection ?: return@macroTrace false
            val collection = when (value) {
                is MacroValue.Array -> value.mapNotNull { it?.stringify(format = Textual) }
                is MacroValue.Dictionary -> value.keys
            }
            val predicates = listOfNotNull<(String) -> Boolean>(
                text
                    ?.evaluate()
                    ?.let { text -> { it.contains(text, ignoreCase = ignoreCase) } },
                regex
                    ?.let { if (ignoreCase) Regex(it.pattern, it.options + RegexOption.IGNORE_CASE) else it }
                    ?.let { regex -> { it.matches(regex) } }
            )
            collection.any { element -> predicates.all { it(element) } }
        }
    }



    @Serializable
    @SerialName("notContains")
    data class NotContains(
        val notContains: ExpressionString,
        val text: TemplateString? = null,
        val regex: Regex? = null,
        val ignoreCase: Boolean = false
    ) : MacroCondition by Not(Contains(
        contains = notContains,
        text = text,
        regex = regex,
        ignoreCase = ignoreCase
    ))

    @Serializable
    @SerialName("exists")
    data class Exists(val exists: TemplateString) : MacroCondition {
        override val knownUsedProperties by lazy { exists.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate() = macroTrace { exists.evaluateToAbsolutePath().exists() }
    }

    @Serializable
    @SerialName("notExists")
    data class NotExists(val notExists: TemplateString) : MacroCondition by Not(Exists(notExists))

    @Serializable
    @SerialName("isDirectory")
    data class IsDirectory(val isDirectory: TemplateString) : MacroCondition {
        override val knownUsedProperties by lazy { isDirectory.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            KnownMacroProperty.EntryName.templateString
            when (isDirectory) {
                KnownMacroProperty.EntryName.templateString, KnownMacroProperty.EntryPath.templateString -> {
                    KnownMacroProperty.EntryType.get().value == KnownMacroProperty.EntryType.Value.DIRECTORY
                }
                else -> isDirectory.evaluateToAbsolutePath().isDirectory()
            }
        }
    }

    @Serializable
    @SerialName("isNotDirectory")
    data class IsNotDirectory(val isNotDirectory: TemplateString) : MacroCondition by Not(IsDirectory(isNotDirectory))

    @Serializable
    @SerialName("isFile")
    data class IsFile(val isFile: TemplateString) : MacroCondition {
        override val knownUsedProperties by lazy { isFile.knownUsedProperties() }

        context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
        override fun evaluate(): Boolean = macroTrace {
            when (isFile) {
                KnownMacroProperty.EntryName.templateString, KnownMacroProperty.EntryPath.templateString -> {
                    KnownMacroProperty.EntryType.get().value == KnownMacroProperty.EntryType.Value.FILE
                }
                else -> isFile.evaluateToAbsolutePath().isRegularFile()
            }
        }
    }

    @Serializable
    @SerialName("isNotFile")
    data class IsNotFile(val isNotFile: TemplateString) : MacroCondition by Not(IsFile(isNotFile))

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
                    IsSet.serializer(),
                    IsNotSet.serializer(),
                    Contains.serializer(),
                    NotContains.serializer(),
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
