@file:UseSerializers(RegexAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.terminal.success
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.exit
import de.jonasbroeckmann.nav.app.macros.MacroRuntimeContext.Companion.set
import de.jonasbroeckmann.nav.app.macros.StringWithPlaceholders.Companion.evaluateToAbsolutePath
import de.jonasbroeckmann.nav.app.macros.StringWithPlaceholders.Companion.evaluateToAbsolutePathToDirectoryOrNull
import de.jonasbroeckmann.nav.app.openInEditor
import de.jonasbroeckmann.nav.app.runCommand
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultChoicePrompt
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultTextPrompt
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import de.jonasbroeckmann.nav.framework.utils.atomicMove
import de.jonasbroeckmann.nav.framework.utils.children
import de.jonasbroeckmann.nav.framework.utils.createDirectories
import de.jonasbroeckmann.nav.framework.utils.delete
import de.jonasbroeckmann.nav.framework.utils.deleteRecursively
import de.jonasbroeckmann.nav.framework.utils.exists
import de.jonasbroeckmann.nav.framework.utils.isDirectory
import de.jonasbroeckmann.nav.framework.utils.sink
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable(with = MacroAction.Companion::class)
sealed interface MacroAction : MacroRunnable {
    @Serializable
    @SerialName("prompt")
    data class Prompt(
        val prompt: StringWithPlaceholders,
        val format: Regex? = null,
        val default: StringWithPlaceholders? = null,
        val choices: List<StringWithPlaceholders> = emptyList(),
        val resultTo: String = DefaultMacroSymbols.ResultDefault.name
    ) : MacroAction {
        init {
            require(listOfNotNull(format, choices.takeIf { it.isNotEmpty() }).size <= 1) {
                "Only one of '${::format.name}' or '${::choices.name}' can be set"
            }
        }

        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val result = if (choices.isNotEmpty()) {
                val evaluatedChoices = choices.map { it.evaluate() }
                context.showMacroDialog {
                    defaultChoicePrompt(
                        title = prompt.evaluate(),
                        choices = evaluatedChoices,
                        defaultChoice = default?.evaluate()?.let { default -> evaluatedChoices.indexOf(default).takeIf { it >= 0 } } ?: 0
                    )
                }
            } else {
                context.showMacroDialog {
                    defaultTextPrompt(
                        title = prompt.evaluate(),
                        initialText = default?.evaluate() ?: "",
                        placeholder = null,
                        validate = { input -> format?.matches(input) ?: true }
                    )
                }
            }
            if (result == null) {
                context.reportDebug { "Aborting macro because prompt was cancelled." }
                context.doReturn()
            }
            context[resultTo] = result
        }
    }

    @Serializable
    @SerialName("macro")
    data class RunMacro(
        val macro: StringWithPlaceholders,
        val ignoreCondition: Boolean = false,
        val parameters: Map<String, StringWithPlaceholders>? = null,
        val capture: Map<String, StringWithPlaceholders>? = null,
        val continueOnReturn: Boolean = true
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val macroId = macro.evaluate()
            val macro = context.identifiedMacros[macroId]
                ?: throw MacroException("No macro with ${Macro::id.name} '$macroId' found")
            context.call(
                parameters = parameters?.mapKeys { (name, _) -> MacroSymbol.fromString(name) },
                capture = capture?.mapKeys { (name, _) -> MacroSymbol.fromString(name) },
                returnBarrier = continueOnReturn,
                runnable = Delegate(
                    macro = macro,
                    ignoreCondition = ignoreCondition
                )
            )
        }

        data class Delegate(
            val macro: Macro,
            val ignoreCondition: Boolean,
        ) : MacroRunnable {
            context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
            override fun run() {
                if (ignoreCondition || macro.available()) {
                    macro.run()
                } else {
                    context.printlnOnDebug {
                        "Skipping macro '${macro.id}' because its condition was not met."
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("command")
    data class RunCommand(
        val command: StringWithPlaceholders,
        val exitCodeTo: String = DefaultMacroSymbols.ExitCode.name,
        val outputTo: String? = null,
        val errorTo: String? = null,
        val trimTrailingNewline: Boolean = true
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val result = runCommand(
                command = command.evaluate(),
                collectOutput = outputTo != null,
                collectError = errorTo != null
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
        val ignoreCase: Boolean = false,
        val groupsTo: List<String> = emptyList()
    ) : MacroAction {
        private val regex by lazy {
            if (ignoreCase) Regex(match.pattern, match.options + RegexOption.IGNORE_CASE) else match
        }

        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val toMatch = value.evaluate()
            val result = regex.matchEntire(toMatch)
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
        val exitCodeTo: String = DefaultMacroSymbols.ExitCode.name
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val exitCode = openInEditor(open.evaluateToAbsolutePath())
            context[exitCodeTo] = exitCode?.toString().orEmpty()
        }
    }

    @Serializable
    @SerialName("writeFile")
    data class WriteFile(
        val writeFile: StringWithPlaceholders,
        val content: StringWithPlaceholders? = null,
        val append: Boolean = false,
        val overwrite: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = writeFile.evaluateToAbsolutePath()
            if (path.isDirectory()) {
                if (!silent) context.reportWarning("Cannot write file because it is a directory: $path")
                return
            }

            fun RawSink.writeAndClose() = use {
                val content = content?.evaluate()?.takeUnless { it.isEmpty() }
                if (content != null) {
                    buffered().use { it.writeString(content) }
                }
            }

            when {
                append -> path.sink(append = true).writeAndClose()
                overwrite -> path.sink(append = false).writeAndClose()
                !overwrite -> {
                    if (path.exists()) {
                        if (!silent) context.reportWarning("Cannot write file because it already exists: $path")
                        return
                    }
                    path.sink(append = false).writeAndClose()
                }
            }
            updateState { updatedEntries { it.path == path } }
        }
    }

    @Serializable
    @SerialName("createDirectory")
    data class CreateDirectory(
        val createDirectory: StringWithPlaceholders,
        val createParents: Boolean = true,
        val silent: Boolean = false
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = createDirectory.evaluateToAbsolutePath()
            if (createParents) {
                path.createDirectories()
            } else {
                if (path.parent?.exists() == false) {
                    if (!silent) context.reportWarning("Cannot create directory because its parents do not exist: $path")
                    return
                }
                path.createDirectories()
            }
            updateState { updatedEntries { it.path == path } }
        }
    }

    @Serializable
    @SerialName("move")
    data class Move(
        val move: StringWithPlaceholders,
        val to: StringWithPlaceholders,
        val createParents: Boolean = true,
        val overwrite: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() {
            val source = move.evaluateToAbsolutePath()
            val destination = to.evaluateToAbsolutePath()
            if (destination.exists() && !overwrite) {
                if (!silent) context.reportWarning("Cannot move item because destination already exists: $destination")
                return
            }
            if (createParents) {
                destination.parent?.createDirectories()
            }
            source.atomicMove(destination)
            updateState { updatedEntries { it.path == destination } }
        }
    }

    @Serializable
    @SerialName("delete")
    data class Delete(
        val delete: StringWithPlaceholders,
        val recursive: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = delete.evaluateToAbsolutePath()
            if (!path.exists()) {
                if (!silent) context.reportWarning("Cannot delete item because it does not exist: $path")
                return
            }
            if (path.isDirectory() && !recursive && path.children().isNotEmpty()) {
                if (!silent) context.reportWarning("Cannot delete directory non-recursively because it is not empty: $path")
                return
            }
            if (recursive) {
                path.deleteRecursively()
            } else {
                path.delete()
            }
            updateState { updatedEntries() }
        }
    }

    @Serializable
    @SerialName("childrenOf")
    data class ChildrenOf(
        val childrenOf: StringWithPlaceholders,
        val fullPath: Boolean = false,
        val resultTo: String = DefaultMacroSymbols.ResultDefault.name
    ) : MacroAction {
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val path = childrenOf.evaluateToAbsolutePath()
            context[resultTo] = when {
                path.isDirectory() -> when {
                    fullPath -> path.children().joinToString("\n")
                    else -> path.children().joinToString("\n") { it.name }
                }
                else -> ""
            }
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

        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            set.forEach { (variable, value) ->
                context[variable] = value.evaluate()
            }
        }

        companion object {
            operator fun invoke(vararg pairs: Pair<String, StringWithPlaceholders>) = Set(mapOf(*pairs))
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
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
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
            @SerialName("info")
            Info,

            @SerialName("success")
            Success,

            @SerialName("warning")
            Warning,

            @SerialName("error")
            Error,
        }

        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
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
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
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
        context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            if (exit) {
                exit(atDirectory = at?.evaluateToAbsolutePathToDirectoryOrNull())
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
                    WriteFile.serializer(),
                    CreateDirectory.serializer(),
                    Move.serializer(),
                    Delete.serializer(),
                    ChildrenOf.serializer(),
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
