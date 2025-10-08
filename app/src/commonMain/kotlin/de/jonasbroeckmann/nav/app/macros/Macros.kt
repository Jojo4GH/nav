@file:UseSerializers(
    KeyboardEventAsStringSerializer::class,
    RegexAsStringSerializer::class
)

package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlMap
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
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.KeyboardEventAsStringSerializer
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.metadataOrNull
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

@Serializable
data class Macro(
    val name: String? = null,
    val description: StringWithPlaceholders? = null,
    val hidden: Boolean = false,
    val quickModeKey: KeyboardEvent? = null,
    val nonQuickModeKey: KeyboardEvent? = null,
    @SerialName("if")
    val condition: MacroCondition? = null,
    @SerialName("run")
    val actions: MacroActions = MacroActions()
) : MacroRunnable {
    init {
        require(hidden || description != null) {
            "Non-hidden macros must have a ${::description.name}"
        }
//        require((quickModeKey != null) implies (description != null)) {
//            "Macros with a ${::quickModeKey.name} must have a ${::description.name}"
//        }
//        require((nonQuickModeKey != null) implies (description != null)) {
//            "Macros with a ${::nonQuickModeKey.name} must have a ${::description.name}"
//        }
    }

    context(scope: MacroVariableScope)
    fun available() = condition == null || condition.evaluate()

    val dependsOnEntry by lazy {
        val usedVariablesInCondition = condition?.usedVariables.orEmpty()
        listOf(
            DefaultMacroVariable.EntryPath,
            DefaultMacroVariable.EntryName,
            DefaultMacroVariable.EntryType
        ).any {
            it.label in usedVariablesInCondition
        }
    }

    context(context: MacroRuntimeContext)
    override fun run() = actions.run()
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
    data class Equal(val equal: List<StringWithPlaceholders>) : MacroCondition {
        init {
            require(equal.size >= 2) { "${::equal.name} must have at least two elements to compare" }
        }

        override val usedVariables: Set<String> by lazy { equal.flatMapTo(mutableSetOf()) { it.placeholders } }

        context(scope: MacroVariableScope)
        override fun evaluate(): Boolean {
            val toCompare = equal.map { it.evaluate() }
            return toCompare.all { it == toCompare[0] }
        }
    }

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
    @SerialName("blank")
    data class Blank(val blank: StringWithPlaceholders) : MacroCondition {
        override val usedVariables by lazy { blank.placeholders.toSet() }

        context(scope: MacroVariableScope)
        override fun evaluate() = blank.evaluate().isBlank()
    }

    companion object : YamlContentPolymorphicSerializer<MacroCondition>(MacroCondition::class) {
        override fun selectDeserializer(node: YamlNode) = when (node) {
            is YamlMap -> {
                val serializers = listOf(
                    Any.serializer(),
                    All.serializer(),
                    Not.serializer(),
                    Equal.serializer(),
                    Match.serializer(),
                    Empty.serializer(),
                    Blank.serializer(),
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
        val ignoreCondition: Boolean = false,
        val mode: Mode = Mode.NoInline
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
            @SerialName("separate") Separate,
        }

        context(context: MacroRuntimeContext)
        override fun run() {
            val macroName = macro.evaluate()
            val macro = context.namedMacros[macroName]
                ?: throw IllegalArgumentException("No macro with ${Macro::name.name} '$macroName' found")

            context(context: MacroRuntimeContext)
            fun runMacro() {
                if (!ignoreCondition && !macro.available()) {
                    context.printlnOnDebug { "Skipping macro '$macroName' because its condition was not met." }
                }
                macro.run()
            }

            when (mode) {
                Mode.Inline -> runMacro()
                Mode.NoInline -> context.call { runMacro() }
                Mode.Separate -> context.call(newContext = true) { runMacro() }
            }
        }
    }

    @Serializable
    @SerialName("command")
    data class RunCommand(
        val command: StringWithPlaceholders,
        val exitCodeTo: String = DefaultMacroVariable.ExitCode.label,
        val outputTo: String? = null,
        val errorTo: String? = null
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            val result = context.run(
                AppAction.RunCommand(command.evaluate()) {
                    this
                        .stdout(if (outputTo != null) Pipe else Inherit)
                        .stderr(if (errorTo != null) Pipe else Inherit)
                }
            )
            context[exitCodeTo] = result?.exitCode?.toString()
            if (outputTo != null) {
                context[outputTo] = result?.stdout
            }
            if (errorTo != null) {
                context[errorTo] = result?.stderr
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
            context[exitCodeTo] = exitCode?.toString()
        }
    }

    @Serializable
    @SerialName("set")
    data class SetVariables(
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
    @SerialName("update")
    data class UpdateState(
        val update: StateUpdate
    ) : MacroAction {
        @Serializable
        data class StateUpdate(
            val directory: StringWithPlaceholders? = null,
            val cursorPosition: StringWithPlaceholders? = null,
            val menuCursorPosition: StringWithPlaceholders? = null,
            val filter: StringWithPlaceholders? = null,
            val command: StringWithPlaceholders? = null,
        )

        context(context: MacroRuntimeContext)
        override fun run() {
            fun <T> T.updater(block: State.(T) -> State): State.() -> State = { block(this@updater)}
            val updaters = listOfNotNull(
                update.directory?.evaluate()?.parsePathToDirectoryOrNull()?.updater { navigateTo(it) },
                update.cursorPosition?.evaluate()?.toIntOrNull()?.updater { withCursorCoerced(it) },
                update.menuCursorPosition?.evaluate()?.toIntOrNull()?.updater { withMenuCursorCoerced(it) },
                update.filter?.evaluate()?.updater { withFilter(it) },
                update.command?.evaluate()?.updater { withCommand(it.takeUnless { it.isEmpty() }) },
            )
            context.run(AppAction.UpdateState {
                updaters.fold(this) { state, updater -> state.updater() }
            })
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
                    OpenFile.serializer(),
                    SetVariables.serializer(),
                    UpdateState.serializer(),
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

context(context: PartialContext)
private fun String.parsePathToDirectoryOrNull(): Path? {
    val path = Path(this).absolute().cleaned()
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
    val fixedValue: (context(FullContext, StateProvider) () -> String?)? = null
) {
    // From context
    WorkingDirectory("workingDirectory", { de.jonasbroeckmann.nav.utils.WorkingDirectory.toString() }),
    StartingDirectory("startingDirectory", { context.startingDirectory.toString() }),
    DebugMode("debugMode", { context.debugMode.toString() }),
    Shell("shell", { context.shell?.shell }),

    // From state
    Directory("directory", { state.directory.toString() }),
    EntryPath("entryPath", { state.currentEntry?.path?.toString() }),
    EntryName("entryName", { state.currentEntry?.path?.name }),
    EntryType("entryType", {
        when (state.currentEntry?.type) {
            Entry.Type.Directory -> "directory"
            Entry.Type.RegularFile -> "file"
            Entry.Type.SymbolicLink -> "link"
            Entry.Type.Unknown -> "unknown"
            null -> null
        }
    }),
    Filter("filter", { state.filter }),
    FilteredEntriesCount("filteredEntriesCount", { state.filteredItems.size.toString() }),
    Command("command", { state.command }),
    EntryCursorPosition("entryCursorPosition", { state.cursor.toString() }),
    MenuCursorPosition("menuCursorPosition", { state.coercedMenuCursor.toString() }),

    // Local
    ExitCode("exitCode");

    val placeholder by lazy { StringWithPlaceholders.placeholder(name) }

    companion object {
        val ByLabel = entries.associateBy { it.label }
    }
}

interface MacroVariableScope {
    operator fun get(variable: String): String

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        fun <R> empty(block: MacroVariableScope.() -> R): R = object : MacroVariableScopeBase(context, stateProvider) {
            override val variables = emptyMap<String, String>()
        }.block()
    }
}

abstract class MacroVariableScopeBase(
    context: FullContext,
    stateProvider: StateProvider
) : MacroVariableScope, FullContext by context, StateProvider by stateProvider {

    protected abstract val variables: Map<String, String>

    override operator fun get(variable: String): String {
        val fixedValue = DefaultMacroVariable.ByLabel[variable]?.fixedValue
            ?: return variables[variable].orEmpty()
        return fixedValue(this, this).orEmpty()
    }
}

class MacroRuntimeContext private constructor(
    private val app: App
) : MacroVariableScopeBase(app, app) {
    override val variables = mutableMapOf<String, String>()

    operator fun set(variable: String, value: String?) {
        variables[variable] = value.orEmpty()
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
value class StringWithPlaceholders(private val raw: String) : MacroEvaluable<String> {
    val placeholders get() = PlaceholderRegex.findAll(raw).map { it.groupValues[1] }

    context(scope: MacroVariableScope)
    override fun evaluate() = raw.replace(PlaceholderRegex) { matchResult ->
        scope[matchResult.groupValues[1]]
    }

    override fun toString() = raw

    companion object {
        private val PlaceholderRegex = Regex("""\{\{(.+?)\}\}""")

        fun placeholder(name: String) = StringWithPlaceholders("{{${name}}}")
    }
}

object RegexAsStringSerializer : KSerializer<Regex> {
    override val descriptor get() = PrimitiveSerialDescriptor(Regex::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Regex) = encoder.encodeString(value.pattern)

    override fun deserialize(decoder: Decoder) = Regex(decoder.decodeString())
}
