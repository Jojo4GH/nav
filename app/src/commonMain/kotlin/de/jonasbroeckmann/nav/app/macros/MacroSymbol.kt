package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.MacroExpression
import de.jonasbroeckmann.nav.app.macros.MacroPathExpression.Operator
import de.jonasbroeckmann.nav.command.Logger
import de.jonasbroeckmann.nav.command.warningOnDebug
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.utils.sink
import de.jonasbroeckmann.nav.framework.utils.source
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.setEnvironmentVariable
import kotlinx.io.files.Path
import kotlinx.io.okio.asOkioSink
import kotlinx.io.okio.asOkioSource
import kotlinx.serialization.Serializable
import me.alllex.parsus.parser.Grammar
import me.alllex.parsus.parser.ParseError
import me.alllex.parsus.parser.ParsedValue
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.and
import me.alllex.parsus.parser.choose
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.maybe
import me.alllex.parsus.parser.or
import me.alllex.parsus.parser.parseOrNull
import me.alllex.parsus.parser.parser
import me.alllex.parsus.parser.unaryMinus
import me.alllex.parsus.parser.zeroOrMore
import me.alllex.parsus.token.regexToken
import okio.buffer
import okio.use
import kotlin.collections.plus
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
    }
}

fun MacroValue?.stringify(format: MacroValue.StringificationFormat = Representative) = this?.stringify(format) ?: when (format) {
    Representative -> "null"
    Json -> "null"
    Textual -> ""
}

sealed class MacroValueStorageType(open val key: String) {
    override fun toString() = key

    data object Property : MacroValueStorageType("property")
    data object Local : MacroValueStorageType("local")
    data object Session : MacroValueStorageType("session")
    data object Persistent : MacroValueStorageType("persistent")
    data object Environment : MacroValueStorageType("env")
    data class Custom(override val key: String) : MacroValueStorageType(key)
    companion object {
        operator fun invoke(key: String) = when (key) {
            Property.key -> Property
            Local.key -> Local
            Session.key -> Session
            Persistent.key -> Persistent
            Environment.key -> Environment
            else -> Custom(key)
        }

        val MacroValueStorageType?.isLocal get() = this is Local?
    }
}

class MacroValueStorageException(message: String) : Exception(message)

interface MacroValueStorage {
    operator fun get(path: MacroPathExpression): MacroValue?

    operator fun contains(path: MacroPathExpression): Boolean = get(path) != null
}

interface MutableMacroValueStorage : MacroValueStorage {
    operator fun set(path: MacroPathExpression, newValue: MacroValue?)
}



abstract class MutableMacroValueStorageBase : MutableMacroValueStorage {
    override fun get(path: MacroPathExpression) = get().evaluate(path)

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        update { computeUpdated(path, newValue) }
    }

    protected abstract fun get(): MacroValue

    protected abstract fun update(updater: MacroValue.() -> MacroValue)
}

class EnvironmentMacroValueStorage(
    private val logger: Logger
) : MutableMacroValueStorage {
    override fun get(path: MacroPathExpression): MacroValue.Text? = path.environmentVariable()
        ?.let { getEnvironmentVariable(it) }
        ?.let { MacroValue.Text(it) }

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        path.environmentVariable()?.let { variable ->
            if (newValue !is MacroValue.Text?) {
                logger.warningOnDebug { "Invalid non-text value for environment variable: ${newValue.stringify()}" }
                return
            }
            setEnvironmentVariable(variable, newValue?.value)
        }
    }

    private fun MacroPathExpression.environmentVariable(): String? {
        val operator = operators.singleOrNull()
        if (operator !is Operator.Key) {
            logger.warningOnDebug { "Invalid path expression for environment variable: $this" }
            return null
        }
        return operator.key
    }
}

//class PropertyStorage(
//    private val fullContext: FullContext,
//    private val stateProvider: StateProvider,
//    properties: List<MacroProperty<*>>
//) : MutableMacroValueStorage {
//    private val properties = properties.associateBy { it.name }
//
//    override fun get(path: MacroPathExpression): MacroValue? = context(fullContext, stateProvider) {
//        path.simpleKey()?.let { properties[it] }?.get()
//    }
//
//    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
//        context(fullContext, stateProvider) {
//            path.simpleKey()?.let { properties[it] }?.trySet()
//        }
//    }
//
//    private fun MacroPathExpression.simpleKey(): String? = (operators.singleOrNull() as? Operator.Key)?.key
//}

class InMemoryMacroValueStorage(
    initial: Map<String, MacroValue> = emptyMap()
) : MutableMacroValueStorageBase() {
    private var value = MacroValue.Dictionary(initial)

    override fun get() = value

    override fun update(updater: MacroValue.() -> MacroValue) {
        val new = value.updater()
        if (new !is MacroValue.Dictionary) throw MacroValueStorageException("Root value must be a dictionary")
        value = new
    }

    operator fun set(key: String, newValue: MacroValue?) {
        if (newValue == null) {
            value -= key
        } else {
            value += (key to newValue)
        }
    }

    fun toMap(): Map<MacroPathExpression, MacroValue> = value.mapKeys { (key, _) -> MacroPathExpression(Operator.Key(key)) }

    fun copy() = InMemoryMacroValueStorage(value)
}

private class DelegatedMap<K, out V>(
    private val delegate: () -> Map<K, V>
) : Map<K, V> {
    override val size get() = delegate().size
    override val entries get() = delegate().entries
    override val keys get() = delegate().keys
    override val values get() = delegate().values

    override fun get(key: K) = delegate()[key]

    override fun containsKey(key: K) = delegate().containsKey(key)

    override fun containsValue(value: @UnsafeVariance V) = delegate().containsValue(value)

    override fun isEmpty() = delegate().isEmpty()
}

class YamlFileMacroValueStorage(
    private val file: Path
) : MutableMacroValueStorageBase() {
    private var cached: MacroValue? = null

    override fun get(): MacroValue {
        var cached = this.cached
        if (cached == null) {
            cached = file.source().asOkioSource().use { Config.Yaml.decodeFromSource<MacroValue>(it) }
            this.cached = cached
        }
        return cached
    }

    override fun update(updater: MacroValue.() -> MacroValue) {
        val old = get()
        cached = old.updater()
        if (cached != old) {
            file.sink().asOkioSink().buffer().use {
                Config.Yaml.encodeToBufferedSink(cached, it)
            }
        }
    }
}

private fun MacroValue.evaluate(path: MacroPathExpression) = path.operators.fold<_, MacroValue?>(this) { value, operator ->
    when (operator) {
        is Operator.Key if value is MacroValue.Dictionary -> value[operator.key]
        is Operator.Index if value is MacroValue.Array -> value.getOrNull(operator.index)
        is Operator.Function -> when (operator) {
            Last if value is MacroValue.Array -> value.lastOrNull()
            Next if value is MacroValue.Array -> null
            Keys if value is MacroValue.Dictionary -> MacroValue.Array(value.keys.map { MacroValue.Text(it) })
            Values if value is MacroValue.Dictionary -> MacroValue.Array(value.values.toList())
            Size if value is MacroValue.Collection -> MacroValue.Text("${value.size}")
            else -> null
        }
        else -> null
    }
}

private fun MacroValue.computeUpdated(
    path: MacroPathExpression,
    newValue: MacroValue?
): MacroValue {
    return computeUpdated(
        path = emptyList(),
        restPath = path.operators,
        newValue = newValue
    ) ?: MacroValue.Dictionary()
}

private data class MacroValueUpdateContext(
    val currentPath: MacroPathExpression,
    val operator: Operator?,
    val restPath: List<Operator>,
    val newValue: MacroValue?
)

private fun MacroValue?.computeUpdated(
    path: List<Operator>,
    restPath: List<Operator>,
    newValue: MacroValue?
): MacroValue? = enterContext(path, restPath, newValue) { operator ->
    when (operator) {
        is Operator.Key -> {
            if (this !is MacroValue.Dictionary?) throwUnexpectedType("dictionary")
            (this ?: MacroValue.Dictionary()).updated(operator.key) {
                it.computeUpdated()
            }
        }
        is Operator.Index -> {
            if (this !is MacroValue.Array?) throwUnexpectedType("array")
            (this ?: MacroValue.Array()).updated(operator.index) {
                it.computeUpdated()
            }
        }
        is Operator.Function -> when (operator) {
            Last -> {
                if (this !is MacroValue.Array?) throwUnexpectedType("array")
                val current = this ?: MacroValue.Array()
                if (current.isEmpty()) throwHere("Cannot set last element of empty array")
                current.updated(current.lastIndex) {
                    it.computeUpdated()
                }
            }
            Next -> {
                if (this !is MacroValue.Array?) throwUnexpectedType("array")
                val current = this ?: MacroValue.Array()
                current.updated(current.size) {
                    it.computeUpdated()
                }
            }
            Keys -> throwHere("Cannot use function '${Operator.Function.Keys.name}' here")
            Values -> throwHere("Cannot use function '${Operator.Function.Values.name}' here")
            Size -> throwHere("Cannot use function '${Operator.Function.Size.name}' here")
        }
        null -> newValue
    }
}

private inline fun enterContext(
    path: List<Operator>,
    restPath: List<Operator>,
    newValue: MacroValue?,
    block: context(MacroValueUpdateContext) (Operator?) -> MacroValue?
): MacroValue? = context(
    MacroValueUpdateContext(
        currentPath = MacroPathExpression(restPath.firstOrNull()?.let { path + it } ?: path),
        operator = restPath.firstOrNull(),
        restPath = restPath.drop(1),
        newValue = newValue
    )
) {
    block(contextOf<MacroValueUpdateContext>().operator)
}

context(context: MacroValueUpdateContext)
private fun MacroValue?.computeUpdated() = computeUpdated(
    path = context.currentPath.operators,
    restPath = context.restPath,
    newValue = context.newValue
)

context(context: MacroValueUpdateContext)
private fun throwHere(message: String): Nothing = throw MacroValueStorageException(
    "Cannot update value at '${context.currentPath}': $message"
)

context(context: MacroValueUpdateContext)
private fun MacroValue.throwUnexpectedType(expectedType: String): Nothing = throwHere(
    "Expected $expectedType, but is ${this.description}"
)

class ParserException(message: String) : Exception(message)

data class MacroPathExpression(
    val operators: List<Operator>
) {
    constructor(key: Operator.Key, vararg operators: Operator) : this(listOf(key, *operators))

    constructor(key: String) : this(Operator.Key(key))

    init {
        if (operators.isEmpty()) throw ParserException("Path expression must have at least one operator")
        if (operators.first() !is Key) throw ParserException("Path expression must start with a key")
    }

    sealed interface Operator {
        fun unparse(base: String?): String
        data class Key(val key: String) : Operator {
            override fun unparse(base: String?) = if (base == null) key else "$base.$key"
        }
        data class Index(val index: Int) : Operator {
            init {
                if (index < 0) throw ParserException("Index must be non-negative")
            }
            override fun unparse(base: String?) = if (base == null) "[$index]" else "$base[$index]"
        }
        sealed class Function(val name: String) : Operator {
            data object Last : Function("last")
            data object Next : Function("next")
            data object Keys : Function("keys")
            data object Values : Function("values")
            data object Size : Function("size")

            override fun unparse(base: String?) = if (base == null) "$name()" else "$name($base)"

            companion object {
                operator fun invoke(name: String) = when (name) {
                    Last.name -> Last
                    Next.name -> Next
                    Keys.name -> Keys
                    Values.name -> Values
                    Size.name -> Size
                    else -> throw ParserException("Unknown function: $name")
                }
            }
        }
    }

    val unparsed by lazy {
        operators
            .fold(null) { base: String?, operator -> operator.unparse(base) }
            .orEmpty()
            .let { ExpressionString(it) }
    }

    operator fun plus(operator: Operator) = MacroPathExpression(operators + operator)

    override fun toString() = unparsed.raw
}

data class MacroExpression(
    val storageType: MacroValueStorageType?,
    val path: MacroPathExpression
) : MacroEvaluable<MacroValue?> {
    constructor(
        storageType: MacroValueStorageType?,
        key: Operator.Key,
        vararg operators: Operator
    ) : this(storageType, MacroPathExpression(key, *operators))

    constructor(key: Operator.Key, vararg operators: Operator) : this(null, key, *operators)

    constructor(storageType: MacroValueStorageType?, key: String) : this(storageType, Operator.Key(key))

    constructor(key: String) : this(null, key)

    val expressionString by lazy {
        ExpressionString(listOfNotNull(storageType?.key, path.unparsed).joinToString(":"))
    }

    val templateString by lazy {
        MacroTemplate(MacroTemplate.Placeholder(this)).templateString
    }

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = scope[this]

    override fun toString() = templateString.raw

    companion object {
        fun parse(string: String) = when (val result = MacroExpressionGrammar.parse(string)) {
            is ParseError -> throw ParserException(result.describe())
            is ParsedValue<MacroExpression> -> result.value
        }

        fun parseOrNull(string: String) = MacroExpressionGrammar.parseOrNull(string)
    }
}

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

    private val key by identifier map { Operator.Key(it.text) }

    private val propertyAccess by -dot and identifier map { property -> Operator.Key(property.text) }

    private val indexAccess by -leftBracket and number and -rightBracket map { index -> Operator.Index(index) }

    private val propertyOrIndexAccesses by zeroOrMore(propertyAccess or indexAccess)

    private val functionApplication by parser(firstTokens = setOf(identifier)) {
        val name = identifier()
        leftParentheses()
        val argument = macroPathOperators()
        rightParentheses()
        argument + Operator.Function(name.text)
    }

    private val keyOrFunctionApplication by functionApplication or (key map { listOf(it) })

    private val macroPathOperators: Parser<List<Operator>> by keyOrFunctionApplication and propertyOrIndexAccesses map { (a, b) -> a + b }

    val macroPathExpression: Parser<MacroPathExpression> by macroPathOperators map { MacroPathExpression(it) }

    private val storageTypePrefix by identifier and -colon map { MacroValueStorageType(it.text) }

    val macroExpression: Parser<MacroExpression> by maybe(storageTypePrefix) and macroPathExpression map { (storageType, path) ->
        MacroExpression(storageType, path)
    }

    override val root: Parser<MacroExpression> get() = macroExpression
}
