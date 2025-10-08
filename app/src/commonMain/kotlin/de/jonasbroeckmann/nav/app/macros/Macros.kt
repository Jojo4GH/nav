@file:UseSerializers(
    KeyboardEventAsStringSerializer::class,
    RegexAsStringSerializer::class
)

package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.terminal.success
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.macros.MacroVariable.DelegatedImmutable
import de.jonasbroeckmann.nav.app.macros.MacroVariable.DelegatedMutable
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.KeyboardEventAsStringSerializer
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.div
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.metadataOrNull
import de.jonasbroeckmann.nav.utils.setEnvironmentVariable
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * A macro that can be run in the application.
 *
 * If their [condition] is met, macros are shown in the following places:
 * - In quick macro mode, if a [quickModeKey] is set and is not [hidden]
 * - In key hints, if a [nonQuickModeKey] is set and is not [hidden]
 * - In the menu, if not [hidden]
 *
 * @property id An optional identifier for the macro. If set, it can be referenced by other macros.
 * @property description A human-readable short description of what the macro does. Can contain placeholders for variables.
 * @property hidden If true, the macro will not be shown.
 * @property quickModeKey The key that triggers the macro when in quick macro mode.
 * @property nonQuickModeKey The key that triggers the macro when not in quick macro mode.
 * @property condition An optional condition that must be met for the macro to be available.
 * @property actions The actions to run when the macro is executed.
 */
@Serializable
data class Macro(
    val id: String? = null,
    val description: StringWithPlaceholders = Empty,
    val hidden: Boolean = false,
    val quickModeKey: KeyboardEvent? = null,
    val nonQuickModeKey: KeyboardEvent? = null,
    val condition: MacroCondition? = null,
    @SerialName("run")
    val actions: MacroActions = MacroActions()
) : MacroRunnable {
    init {
        require(hidden || description.raw.isNotBlank()) {
            "Non-hidden macros must have a ${::description.name}"
        }
    }

    context(scope: MacroVariableScope)
    fun available() = condition == null || condition.evaluate()

    private val usedVariablesInDescriptionOrCondition by lazy {
        description.placeholders.toSet() + condition?.usedVariables.orEmpty()
    }

    val dependsOnEntry by lazy {
        listOf(
            DefaultMacroVariable.EntryPath,
            DefaultMacroVariable.EntryName,
            DefaultMacroVariable.EntryType
        ).any {
            it.label in usedVariablesInDescriptionOrCondition
        }
    }

    val dependsOnFilter by lazy {
        DefaultMacroVariable.Filter.label in usedVariablesInDescriptionOrCondition
    }

    context(context: MacroRuntimeContext)
    override fun run() = actions.run()
}

object DefaultMacros {
    val RunCommand = Macro(
        id = "navRunCommand",
        hidden = true,
        actions = MacroActions(
            MacroAction.RunCommand(command = DefaultMacroVariable.Command.placeholder),
            MacroAction.If(
                condition = MacroCondition.Not(
                    MacroCondition.Equal(
                        listOf(
                            DefaultMacroVariable.ExitCode.placeholder,
                            StringWithPlaceholders("0")
                        )
                    )
                ),
                then = MacroActions(
                    MacroAction.Print(
                        print = StringWithPlaceholders("Received exit code ${DefaultMacroVariable.ExitCode.placeholder}"),
                        style = MacroAction.Print.Style.Error
                    )
                )
            ),
            MacroAction.Set(
                set = mapOf(
                    DefaultMacroVariable.Command.label to StringWithPlaceholders("")
                )
            )
        )
    )
}

@Serializable(with = MacroCondition.Companion::class)
sealed interface MacroCondition : MacroEvaluable<Boolean> {

    val usedVariables: Set<String>

    @Serializable
    @SerialName("any")
    data class Any(val any: List<MacroCondition>) : MacroCondition {
        override val usedVariables: Set<String> by lazy { any.flatMapTo(mutableSetOf()) { it.usedVariables } }

        context(scope: MacroVariableScope)
        override fun evaluate() = any.any { it.evaluate() }
    }

    @Serializable
    @SerialName("all")
    data class All(val all: List<MacroCondition>) : MacroCondition {
        override val usedVariables: Set<String> by lazy { all.flatMapTo(mutableSetOf()) { it.usedVariables } }

        context(scope: MacroVariableScope)
        override fun evaluate() = all.all { it.evaluate() }
    }

    @Serializable
    @SerialName("not")
    data class Not(val not: MacroCondition) : MacroCondition {
        override val usedVariables get() = not.usedVariables

        context(scope: MacroVariableScope)
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

        override val usedVariables: Set<String> by lazy { equal.flatMapTo(mutableSetOf()) { it.placeholders } }

        context(scope: MacroVariableScope)
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
        override val usedVariables by lazy { value.placeholders.toSet() }

        context(scope: MacroVariableScope)
        override fun evaluate() = match.matches(value.evaluate())
    }

    @Serializable
    @SerialName("empty")
    data class Empty(val empty: StringWithPlaceholders) : MacroCondition {
        override val usedVariables by lazy { empty.placeholders.toSet() }

        context(scope: MacroVariableScope)
        override fun evaluate() = empty.evaluate().isEmpty()
    }

    @Serializable
    @SerialName("notEmpty")
    data class NotEmpty(val notEmpty: StringWithPlaceholders) : MacroCondition by Not(Empty(notEmpty))

    @Serializable
    @SerialName("blank")
    data class Blank(val blank: StringWithPlaceholders) : MacroCondition {
        override val usedVariables by lazy { blank.placeholders.toSet() }

        context(scope: MacroVariableScope)
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

@Serializable
@JvmInline
value class MacroActions(private val actions: List<MacroAction> = emptyList()) : MacroRunnable {
    constructor(vararg actions: MacroAction) : this(listOf(*actions))

    context(context: MacroRuntimeContext)
    override fun run() {
        actions.forEach { it.run() }
    }
}

@Serializable(with = MacroAction.Companion::class)
sealed interface MacroAction : MacroRunnable {
    @Serializable
    @SerialName("prompt")
    data class Prompt(
        val prompt: StringWithPlaceholders,
        val format: Regex? = null,
        val default: StringWithPlaceholders? = null,
        val choices: List<StringWithPlaceholders> = emptyList(),
        val resultTo: String = "result"
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            TODO("Not yet implemented")
        }
    }

    @Serializable
    @SerialName("macro")
    data class RunMacro(
        val macro: StringWithPlaceholders,
        val mode: Mode = Mode.NoInline,
        val ignoreCondition: Boolean = false
    ) : MacroAction {
        @Serializable
        enum class Mode {
            /**
             * Context is shared with the parent macro.
             * Returns inside the macro will return from the parent macro as well.
             */
            @SerialName("inline") Inline,
            /**
             * Context is shared with the parent macro.
             * Returns inside the macro will only return from the macro itself.
             */
            @SerialName("noinline") NoInline,
            /**
             * Context is separate from the parent macro.
             * Returns inside the macro will only return from the macro itself.
             */
            @SerialName("independent") Independent,
        }

        context(context: MacroRuntimeContext)
        override fun run() {
            val macroId = macro.evaluate()
            val macro = context.identifiedMacros[macroId]
                ?: throw IllegalArgumentException("No macro with ${Macro::id.name} '$macroId' found")

            context(context: MacroRuntimeContext)
            fun runMacro() {
                if (!ignoreCondition && !macro.available()) {
                    context.printlnOnDebug { "Skipping macro '$macroId' because its condition was not met." }
                } else {
                    macro.run()
                }
            }

            when (mode) {
                Mode.Inline -> runMacro()
                Mode.NoInline -> context.call { runMacro() }
                Mode.Independent -> context.call(newContext = true) { runMacro() }
            }
        }
    }

    @Serializable
    @SerialName("command")
    data class RunCommand(
        val command: StringWithPlaceholders,
        val exitCodeTo: String = DefaultMacroVariable.ExitCode.label,
        val outputTo: String? = null,
        val errorTo: String? = null,
        val trimTrailingNewline: Boolean = true
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            val result = context.run(
                AppAction.RunCommand(
                    command = command.evaluate(),
                    collectOutput = outputTo != null,
                    collectError = errorTo != null
                )
            )
            context[exitCodeTo] = result?.exitCode?.toString().orEmpty()
            if (outputTo != null) {
                context[outputTo] = result?.stdout.orEmpty().let {
                    if (trimTrailingNewline) {
                        when {
                            it.endsWith("\r\n") -> it.dropLast(2)
                            it.endsWith('\r') -> it.dropLast(1)
                            it.endsWith('\n') -> it.dropLast(1)
                            else -> it
                        }
                    } else {
                        it
                    }
                }
            }
            if (errorTo != null) {
                context[errorTo] = result?.stderr.orEmpty()
            }
        }
    }

    @Serializable
    @SerialName("match")
    data class Match(
        val match: Regex,
        @SerialName("in")
        val value: StringWithPlaceholders,
        val groupsTo: List<String> = emptyList()
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            val toMatch = value.evaluate()
            val result = match.matchEntire(toMatch)
            result?.groupValues?.forEachIndexed { index, string ->
                val destination = groupsTo.getOrNull(index - 1) ?: return@forEachIndexed
                context[destination] = string
            }
        }
    }

    @Serializable
    @SerialName("open")
    data class OpenFile(
        val open: StringWithPlaceholders,
        val exitCodeTo: String = DefaultMacroVariable.ExitCode.label
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            val exitCode = context.run(AppAction.OpenFile(Path(open.evaluate())))
            context[exitCodeTo] = exitCode?.toString().orEmpty()
        }
    }

    @Serializable
    @SerialName("set")
    data class Set(
        val set: Map<String, StringWithPlaceholders>
    ) : MacroAction {
        init {
            set.forEach { (variable, value) ->
                value.placeholders.forEach { placeholder ->
                    if (placeholder != variable) {
                        require(placeholder !in set) {
                            "Circular dependency: " +
                                "Variable '$variable' depends on '$placeholder' which is also being set in the same action."
                        }
                    }
                }
            }
        }

        context(context: MacroRuntimeContext)
        override fun run() {
            set.forEach { (variable, value) ->
                context[variable] = value.evaluate()
            }
        }
    }

    @Serializable
    @SerialName("if")
    data class If(
        @SerialName("if")
        val condition: MacroCondition,
        val then: MacroActions = MacroActions(),
        @SerialName("else")
        val otherwise: MacroActions = MacroActions()
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            if (condition.evaluate()) {
                then.run()
            } else {
                otherwise.run()
            }
        }
    }

    @Serializable
    @SerialName("print")
    data class Print(
        val print: StringWithPlaceholders,
        val style: Style? = null,
        val debug: Boolean = false
    ) : MacroAction {
        @Serializable
        enum class Style {
            @SerialName("info") Info,
            @SerialName("success") Success,
            @SerialName("warning") Warning,
            @SerialName("error") Error,
        }

        context(context: MacroRuntimeContext)
        override fun run() {
            if (debug && !context.debugMode) return
            val message = print.evaluate()
            when (style) {
                null -> context.terminal.println(message)
                Style.Info -> context.terminal.info(message)
                Style.Success -> context.terminal.success(message)
                Style.Warning -> context.terminal.warning(message)
                Style.Error -> context.terminal.danger(message)
            }
        }
    }

    @Serializable
    @SerialName("return")
    data class Return(
        @SerialName("return")
        val doReturn: Boolean = true,
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            if (doReturn) {
                context.doReturn()
            }
        }
    }

    @Serializable
    @SerialName("exit")
    data class Exit(
        val exit: Boolean = true,
        val at: StringWithPlaceholders? = null
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            if (exit) {
                context.run(AppAction.Exit(at?.evaluate()?.parsePathToDirectoryOrNull()))
            }
        }
    }

    companion object : YamlContentPolymorphicSerializer<MacroAction>(MacroAction::class) {
        override fun selectDeserializer(node: YamlNode) = when (node) {
            is YamlMap -> {
                val serializers = listOf(
                    Prompt.serializer(),
                    RunMacro.serializer(),
                    RunCommand.serializer(),
                    Match.serializer(),
                    OpenFile.serializer(),
                    Set.serializer(),
                    If.serializer(),
                    Print.serializer(),
                    Return.serializer(),
                    Exit.serializer(),
                )
                serializers.firstOrNull { it.descriptor.serialName in node } ?: throw IllegalArgumentException(
                    "Could not determine type of action at ${node.path.toHumanReadableString()} " +
                        "(must be one of: ${serializers.map { it.descriptor.serialName }})"
                )
            }
            else -> throw IllegalArgumentException("Unexpected node at ${node.path.toHumanReadableString()}")
        }
    }
}

context(context: PartialContext, stateProvider: StateProvider)
private fun String.parsePathToDirectoryOrNull(): Path? {
    val path = try {
        Path(this).let { path ->
            if (path.isAbsolute) {
                path
            } else {
                state.directory / path
            }
        }.absolute()
    } catch (_: FileNotFoundException) {
        context.printlnOnDebug { "\"$this\": No such file or directory" }
        return null
    }
    val metadata = path.metadataOrNull()
    if (metadata == null) {
        context.printlnOnDebug { "\"$this\": No such file or directory" }
        return null
    }
    if (!metadata.isDirectory) {
        context.printlnOnDebug { "\"$this\": Not a directory" }
        return null
    }
    return path
}

enum class DefaultMacroVariable(
    val label: String,
    val variable: () -> MacroVariable = { MacroVariable.Custom(label) }
) {
    // From context
    WorkingDirectory(
        "workingDirectory",
        {
            DelegatedImmutable(
                name = "workingDirectory",
                onGet = { de.jonasbroeckmann.nav.utils.WorkingDirectory.toString() }
            )
        }
    ),
    StartingDirectory(
        "startingDirectory",
        {
            DelegatedImmutable(
                name = "startingDirectory",
                onGet = { context.startingDirectory.toString() }
            )
        }
    ),
    DebugMode(
        "debugMode",
        {
            DelegatedImmutable(
                name = "debugMode",
                onGet = { context.debugMode.toString() }
            )
        }
    ),
    Shell(
        "shell",
        {
            DelegatedImmutable(
                name = "shell",
                onGet = { context.shell?.shell }
            )
        }
    ),

    // From state
    Directory(
        "directory",
        {
            DelegatedMutable(
                name = "directory",
                onGet = { state.directory.toString() },
                onSet = { newValue -> newValue.parsePathToDirectoryOrNull()?.let { updateState { navigateTo(it) } } }
            )
        }
    ),
    EntryPath(
        "entryPath",
        {
            DelegatedImmutable(
                name = "entryPath",
                onGet = { state.currentEntry?.path?.toString() }
            )
        }
    ),
    EntryName(
        "entryName",
        {
            DelegatedImmutable(
                name = "entryName",
                onGet = { state.currentEntry?.path?.name }
            )
        }
    ),
    EntryType(
        "entryType",
        {
            DelegatedImmutable(
                name = "entryType",
                onGet = {
                    when (state.currentEntry?.type) {
                        Entry.Type.Directory -> "directory"
                        Entry.Type.RegularFile -> "file"
                        Entry.Type.SymbolicLink -> "link"
                        Entry.Type.Unknown -> "unknown"
                        null -> null
                    }
                }
            )
        }
    ),
    Filter(
        "filter",
        {
            DelegatedMutable(
                name = "filter",
                onGet = { state.filter },
                onSet = { newValue -> updateState { withFilter(newValue) } }
            )
        }
    ),
    FilteredEntriesCount(
        "filteredEntriesCount",
        {
            DelegatedImmutable(
                name = "filteredEntriesCount",
                onGet = { state.filteredItems.size.toString() }
            )
        }
    ),
    Command(
        "command",
        {
            DelegatedMutable(
                name = "command",
                onGet = { state.command },
                onSet = { newValue -> updateState { withCommand(newValue.takeUnless { it.isEmpty() }) } }
            )
        }
    ),
    EntryCursorPosition(
        "entryCursorPosition",
        {
            DelegatedMutable(
                name = "entryCursorPosition",
                onGet = { state.cursor.toString() },
                onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withCursorCoerced(it) } } }
            )
        }
    ),
    MenuCursorPosition(
        "menuCursorPosition",
        {
            DelegatedMutable(
                name = "menuCursorPosition",
                onGet = { state.coercedMenuCursor.toString() },
                onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withMenuCursorCoerced(it) } } }
            )
        }
    ),

    // Local
    ExitCode("exitCode");

    val placeholder by lazy { StringWithPlaceholders.placeholder(label) }

    companion object {
        val ByName = entries.associateBy { it.label }

        context(context: MacroRuntimeContext)
        private fun updateState(block: State.() -> State) = context.run(AppAction.UpdateState(block))
    }
}

sealed interface MacroVariable {

    val name: String

    context(_: FullContext, _: StateProvider)
    val value: String

    val placeholder get() = StringWithPlaceholders.placeholder(name)

    sealed interface Mutable : MacroVariable {
        context(_: MacroRuntimeContext)
        fun set(value: String)
    }

    data class FromEnvironment(
        override val name: String
    ) : MacroVariable, Mutable {
        private val envName = name.removePrefix(ENV_PREFIX)

        context(_: FullContext, _: StateProvider)
        override val value get() = getEnvironmentVariable(envName).orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) {
            setEnvironmentVariable(envName, value)
        }
    }

    data class DelegatedImmutable(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> String?,
    ) : MacroVariable {
        context(_: FullContext, _: StateProvider)
        override val value get() = onGet().orEmpty()
    }

    data class DelegatedMutable(
        override val name: String,
        private val onGet: context(FullContext, StateProvider) () -> String?,
        private val onSet: context(MacroRuntimeContext) (String) -> Unit
    ) : MacroVariable, Mutable {
        context(_: FullContext, _: StateProvider)
        override val value get() = onGet().orEmpty()

        context(_: MacroRuntimeContext)
        override fun set(value: String) = onSet(value)
    }

    data class Custom(
        override val name: String,
        private var _value: String = ""
    ) : MacroVariable, Mutable {

        context(_: FullContext, _: StateProvider)
        override val value get() = _value

        context(_: MacroRuntimeContext)
        override fun set(value: String) {
            _value = value
        }
    }

    companion object {
        private const val ENV_PREFIX = "env:"

        fun fromName(name: String): MacroVariable = if (name.startsWith(ENV_PREFIX)) {
            FromEnvironment(name)
        } else {
            DefaultMacroVariable.ByName[name]?.variable?.invoke() ?: Custom(name)
        }
    }
}

interface MacroVariableScope {
    operator fun get(name: String): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroVariableScope.() -> R): R = MacroVariableScopeBase(context, stateProvider).block()
    }
}

open class MacroVariableScopeBase(
    context: FullContext,
    stateProvider: StateProvider
) : MacroVariableScope, FullContext by context, StateProvider by stateProvider {

    private val variables = mutableMapOf<String, MacroVariable>()

    protected fun variable(name: String) = variables.getOrPut(name) { MacroVariable.fromName(name) }

    override operator fun get(name: String): String = variable(name).value
}

class MacroRuntimeContext private constructor(
    private val app: App
) : MacroVariableScopeBase(app, app) {

    operator fun set(name: String, value: String) {
        variable(name).let {
            if (it is MacroVariable.Mutable) {
                it.set(value)
            } else {
                terminal.danger("Cannot modify '$name' as it is not mutable.")
            }
        }
    }

    fun <R> run(appAction: AppAction<R>) = appAction.runIn(app)

    fun call(newContext: Boolean = false, runnable: MacroRunnable) {
        val callContext = if (newContext) MacroRuntimeContext(app) else this
        try {
            context(callContext) { runnable.run() }
        } catch (_: MacroReturnEvent) {
            /* no-op */
        }
    }

    fun doReturn(): Nothing = throw MacroReturnEvent()

    companion object {
        context(app: App)
        fun run(runnable: MacroRunnable) {
            MacroRuntimeContext(app).call(runnable = runnable)
        }

        private class MacroReturnEvent : Throwable()
    }
}

fun interface MacroRunnable {
    context(context: MacroRuntimeContext)
    fun run()
}

fun interface MacroEvaluable<R> {
    context(scope: MacroVariableScope)
    fun evaluate(): R
}

private operator fun YamlMap.contains(key: String) = getKey(key) != null

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

object RegexAsStringSerializer : KSerializer<Regex> {
    override val descriptor get() = PrimitiveSerialDescriptor(Regex::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Regex) = encoder.encodeString(value.pattern)

    override fun deserialize(decoder: Decoder) = Regex(decoder.decodeString())
}
