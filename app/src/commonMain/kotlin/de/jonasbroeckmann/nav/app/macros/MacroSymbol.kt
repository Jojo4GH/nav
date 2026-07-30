package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroPathExpression.Operator
import de.jonasbroeckmann.nav.app.macros.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.app.state.StateProvider
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
import me.alllex.parsus.parser.ParseException
import me.alllex.parsus.parser.ParsedValue
import me.alllex.parsus.parser.Parser
import me.alllex.parsus.parser.choose
import me.alllex.parsus.parser.map
import me.alllex.parsus.parser.parser
import me.alllex.parsus.token.literalToken
import me.alllex.parsus.token.regexToken
import okio.buffer
import okio.use
import kotlin.collections.plus
import kotlin.jvm.JvmInline

sealed class MacroSymbol {
    abstract val name: String

    val placeholder by lazy {
        StringWithPlaceholders.placeholder(name)
    }

    override fun toString() = "$placeholder"

    data class Generic(override val name: String) : MacroSymbol()

    data class EnvironmentVariable(
        private val variableName: String
    ) : MacroSymbol(), MacroProperty.Mutable {
        override val name by lazy {
            "$ENV_PREFIX$PREFIX_SEPARATOR$variableName"
        }

        override val symbol get() = this

        context(_: FullContext, _: StateProvider)
        override fun get() = getEnvironmentVariable(variableName).orEmpty()

        context(_: MainController, _: MacroSymbolScope)
        override fun set(value: String) {
            setEnvironmentVariable(variableName, value)
        }
    }

//    data class Persistent(
//        override val name: String
//    )

    companion object {
        private const val PREFIX_SEPARATOR = ':'
        private const val ENV_PREFIX = "env"



        operator fun invoke(string: String) = if (string.startsWith("$ENV_PREFIX$PREFIX_SEPARATOR")) {
            EnvironmentVariable(string.removePrefix("$ENV_PREFIX$PREFIX_SEPARATOR"))
        } else {
            UByte
            Generic(string)
        }

        context(scope: MacroSymbolScope)
        fun MacroSymbol.get() = scope[this]
    }
}

@Serializable
sealed interface MacroValue {
    val description: String

    fun stringify(): String

    @Serializable
    @JvmInline
    value class Text(val value: String = "") : MacroValue, CharSequence by value {
        override val description get() = "text"

        override fun stringify() = value
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
            return if (newValue == null) Dictionary(this - key) else Dictionary(this + (key to newValue))
        }

        override fun stringify() = value.asSequence().joinToString(
            separator = ", ",
            prefix = "{ ",
            postfix = " }"
        ) { "${it.key}: ${it.value.stringify()}" }
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

        override fun stringify() = value.joinToString(
            separator = ", ",
            prefix = "[ ",
            postfix = " ]"
        ) { it?.stringify() ?: "null" }
    }
}

sealed class MacroValueStorageType(open val key: String) {
    override fun toString() = key

    data object Local : MacroValueStorageType("local")
    data object Session : MacroValueStorageType("session")
    data object Persistent : MacroValueStorageType("persistent")
    data object Environment : MacroValueStorageType("env")
    data class Custom(override val key: String) : MacroValueStorageType(key)
    companion object {
        operator fun invoke(key: String) = when (key) {
            Local.key -> Local
            Session.key -> Session
            Persistent.key -> Persistent
            Environment.key -> Environment
            else -> Custom(key)
        }
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

class PropertyStorage(
    private val fullContext: FullContext,
    private val stateProvider: StateProvider,
    properties: List<MacroProperty<*>>
) : MutableMacroValueStorage {
    private val properties = properties.associateBy { it.name }

    override fun get(path: MacroPathExpression): MacroValue? = context(fullContext, stateProvider) {
        path.simpleKey()?.let { properties[it] }?.get()
    }

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        context(fullContext, stateProvider) {
            path.simpleKey()?.let { properties[it] }?.trySet()
        }
    }

    private fun MacroPathExpression.simpleKey(): String? = (operators.singleOrNull() as? Operator.Key)?.key
}

class InMemoryMacroValueStorage(
    initial: Map<String, MacroValue> = emptyMap()
) : MutableMacroValueStorageBase() {
    private var storage: MacroValue = MacroValue.Dictionary(initial)

    override fun get() = storage

    override fun update(updater: MacroValue.() -> MacroValue) {
        storage = storage.updater()
    }
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
    "Cannot update value at '${context.currentPath.unparse()}': $message"
)

context(context: MacroValueUpdateContext)
private fun MacroValue.throwUnexpectedType(expectedType: String): Nothing = throwHere(
    "Expected $expectedType, but is ${this.description}"
)

class ParserException(message: String) : Exception(message)

data class MacroPathExpression(
    val operators: List<Operator>
) {
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

    operator fun plus(operator: Operator) = MacroPathExpression(operators + operator)

    fun unparse() = operators
        .fold(null) { base: String?, operator -> operator.unparse(base) }
        .orEmpty()

    override fun toString() = unparse()
}

data class MacroExpression(
    val storageType: MacroValueStorageType?,
    val path: MacroPathExpression
) : MacroEvaluable<MacroValue?> {

    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    override fun evaluate() = scope[this]

    fun unparse() = listOfNotNull(
        storageType?.key,
        path.unparse()
    ).joinToString(":")

    override fun toString() = unparse()

    companion object {
        fun parse(string: String) = when (val result = MacroExpressionGrammar.parse(string)) {
            is ParseError -> throw ParserException(result.describe())
            is ParsedValue<MacroExpression> -> result.value
        }
    }
}

object MacroExpressionGrammar : Grammar<MacroExpression>() {
    init {
        regexToken("""\s+""", ignored = true)
    }

    val identifier by regexToken("""[A-Za-z_][A-Za-z0-9_]*""")
    val number by regexToken("""\d+""") map {
        it.text.toIntOrNull() ?: throw ParserException("Invalid number: ${it.text}")
    }
    val string by regexToken(""""([^"\\]|\\.)*"""") map {
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
    val dot by regexToken("""\.""")
    val leftBracket by regexToken("""\[""")
    val rightBracket by regexToken("""]""")
    val leftParentheses by regexToken("""\(""")
    val rightParentheses by regexToken("""\)""")
    val colon by regexToken(""":""")

    val propertyAccess by parser {
        val base = macroPathExpression()
        dot()
        val property = identifier()
        base + Operator.Key(property.text)
    }
    val indexAccess by parser {
        val base = macroPathExpression()
        leftBracket()
        val index = number()
        rightBracket()
        base + Operator.Index(index)
    }
    val functionApplication by parser {
        val name = identifier()
        leftParentheses()
        val argument = macroPathExpression()
        rightParentheses()
        argument + Operator.Function(name.text)
    }
    val macroPathExpression: Parser<MacroPathExpression> by parser {
        choose(propertyAccess, indexAccess, functionApplication)
    }

    val storageType by identifier map { MacroValueStorageType(it.text) }

    val macroExpression: Parser<MacroExpression> by parser {
        val storageType = storageType()
        colon()
        val operators = macroPathExpression()
        MacroExpression(storageType, operators)
    }

    override val root: Parser<MacroExpression> get() = macroExpression
}
