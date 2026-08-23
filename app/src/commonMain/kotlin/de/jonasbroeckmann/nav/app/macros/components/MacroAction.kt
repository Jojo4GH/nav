@file:UseSerializers(RegexAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros.components

import com.charleskorn.kaml.YamlContentPolymorphicSerializer
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.MacroException
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.contains
import de.jonasbroeckmann.nav.app.macros.context.MacroCallScope
import de.jonasbroeckmann.nav.app.macros.context.MacroStorageScope.Companion.set
import de.jonasbroeckmann.nav.app.macros.expressions.ExpressionString
import de.jonasbroeckmann.nav.app.macros.macroTrace
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString.Companion.evaluateToAbsolutePath
import de.jonasbroeckmann.nav.app.macros.templates.TemplateString.Companion.evaluateToAbsolutePathToDirectoryOrNull
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.state.updateState
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultChoicePrompt
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultTextPrompt
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.utils.atomicMove
import de.jonasbroeckmann.nav.framework.utils.children
import de.jonasbroeckmann.nav.framework.utils.createDirectories
import de.jonasbroeckmann.nav.framework.utils.delete
import de.jonasbroeckmann.nav.framework.utils.deleteRecursively
import de.jonasbroeckmann.nav.framework.utils.exists
import de.jonasbroeckmann.nav.framework.utils.isDirectory
import de.jonasbroeckmann.nav.framework.utils.sink
import de.jonasbroeckmann.nav.printlnOnDebug
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.writeString
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.collections.component1

@Serializable(with = MacroAction.Companion::class)
sealed interface MacroAction : MacroRunnable {
    @Serializable
    @SerialName("prompt")
    data class Prompt(
        val prompt: TemplateString,
        val format: Regex? = null,
        val default: TemplateString? = null,
        val choices: List<TemplateString> = emptyList(),
        val hideMainTable: Boolean = false,
        val resultTo: ExpressionString = DefaultMacroExpressions.ResultDefault.expressionString,
        val onChoice: Map<TemplateString, MacroActions> = emptyMap()
    ) : MacroAction {
        init {
            require(listOfNotNull(format, choices.takeIf { it.isNotEmpty() }).size <= 1) {
                "Only one of '${::format.name}' or '${::choices.name}' can be set"
            }
        }

        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val dialogOptions = DialogOptions(hideMainTable = hideMainTable)
            val result = if (choices.isNotEmpty()) {
                val evaluatedChoices = choices.map { it.evaluate() }
                scope.showMacroDialog(dialogOptions) {
                    defaultChoicePrompt(
                        title = prompt.evaluate(),
                        choices = evaluatedChoices,
                        defaultChoice = default?.evaluate()?.let { default -> evaluatedChoices.indexOf(default).takeIf { it >= 0 } } ?: 0
                    )
                }
            } else {
                scope.showMacroDialog(dialogOptions) {
                    defaultTextPrompt(
                        title = prompt.evaluate(),
                        initialText = default?.evaluate() ?: "",
                        placeholder = null,
                        validate = { input -> format?.matches(input) ?: true }
                    )
                }
            }
            if (result == null) {
                scope.reportDebug { "Aborting macro because prompt was cancelled." }
                scope.doReturn()
            }
            scope[resultTo] = result
            onChoice.forEach { (case, actions) ->
                if (case.evaluate() == result) {
                    actions.run()
                }
            }
        }
    }

    @Serializable
    @SerialName("macro")
    data class RunMacro(
        val macro: TemplateString,
        val ignoreCondition: Boolean = false,
        val parameters: Map<ExpressionString, TemplateString>? = null,
        val capture: Map<ExpressionString, TemplateString>? = null,
        val continueOnReturn: Boolean = true
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val macroId = Macro.Id(macro.evaluate())
            val macro = scope.macro(macroId)
                ?: throw MacroException("No macro with ${Macro::id.name} '$macroId' found")
            scope.call(
                parameters = parameters?.map { (expressionString, value) ->
                    expressionString.evaluate() to value.asMacroValueEvaluable()
                },
                capture = capture?.map { (expressionString, value) ->
                    expressionString.evaluate() to value.asMacroValueEvaluable()
                },
                returnToRoot = !continueOnReturn,
                callable = MacroCallable(macro = macro) {
                    if (ignoreCondition || macro.available()) {
                        macro.run()
                    } else {
                        contextOf<Logger>().printlnOnDebug {
                            "Skipping macro '${macro.id}' because its condition was not met."
                        }
                    }
                }
            )
        }
    }

    @Serializable
    @SerialName("command")
    data class RunCommand(
        val command: TemplateString,
        val exitCodeTo: ExpressionString = DefaultMacroExpressions.ExitCode.expressionString,
        val outputTo: ExpressionString? = null,
        val errorTo: ExpressionString? = null,
        val trimTrailingNewline: Boolean = true
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val result = scope.controller.runCommand(
                command = command.evaluate(),
                collectOutput = outputTo != null,
                collectError = errorTo != null
            )
            scope[exitCodeTo] = result?.exitCode?.toString().orEmpty()
            if (outputTo != null) {
                scope[outputTo] = result?.stdout.orEmpty().let {
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
                scope[errorTo] = result?.stderr.orEmpty()
            }
        }
    }

    @Serializable
    @SerialName("match")
    data class Match(
        val match: Regex,
        @SerialName("in")
        val value: TemplateString,
        val ignoreCase: Boolean = false,
        val groupsTo: List<ExpressionString> = emptyList()
    ) : MacroAction {
        private val regex by lazy {
            if (ignoreCase) Regex(match.pattern, match.options + RegexOption.IGNORE_CASE) else match
        }

        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val toMatch = value.evaluate()
            val result = regex.matchEntire(toMatch)
            result?.groupValues?.forEachIndexed { index, string ->
                val destination = groupsTo.getOrNull(index - 1) ?: return@forEachIndexed
                scope[destination] = string
            }
        }
    }

    @Serializable
    @SerialName("open")
    data class OpenFile(
        val open: TemplateString,
        val exitCodeTo: ExpressionString = DefaultMacroExpressions.ExitCode.expressionString
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val exitCode = scope.controller.openInEditor(open.evaluateToAbsolutePath())
            scope[exitCodeTo] = exitCode?.toString().orEmpty()
        }
    }

    @Serializable
    @SerialName("writeFile")
    data class WriteFile(
        val writeFile: TemplateString,
        val content: TemplateString? = null,
        val append: Boolean = false,
        val overwrite: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = writeFile.evaluateToAbsolutePath()
            if (path.isDirectory()) {
                if (!silent) scope.reportWarning { "Cannot write file because it is a directory: $path" }
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
                        if (!silent) scope.reportWarning { "Cannot write file because it already exists: $path" }
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
        val createDirectory: TemplateString,
        val createParents: Boolean = true,
        val silent: Boolean = false
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = createDirectory.evaluateToAbsolutePath()
            if (createParents) {
                path.createDirectories()
            } else {
                if (path.parent?.exists() == false) {
                    if (!silent) scope.reportWarning { "Cannot create directory because its parents do not exist: $path" }
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
        val move: TemplateString,
        val to: TemplateString,
        val createParents: Boolean = true,
        val overwrite: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() {
            val source = move.evaluateToAbsolutePath()
            val destination = to.evaluateToAbsolutePath()
            if (destination.exists() && !overwrite) {
                if (!silent) scope.reportWarning { "Cannot move item because destination already exists: $destination" }
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
        val delete: TemplateString,
        val recursive: Boolean = false,
        val silent: Boolean = false
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val path = delete.evaluateToAbsolutePath()
            if (!path.exists()) {
                if (!silent) scope.reportWarning { "Cannot delete item because it does not exist: $path" }
                return
            }
            if (path.isDirectory() && !recursive && path.children().isNotEmpty()) {
                if (!silent) scope.reportWarning { "Cannot delete directory non-recursively because it is not empty: $path" }
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
        val childrenOf: TemplateString,
        val fullPath: Boolean = false,
        val resultTo: ExpressionString = DefaultMacroExpressions.ResultDefault.expressionString
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            val path = childrenOf.evaluateToAbsolutePath()
            scope[resultTo] = when {
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
        val set: Map<ExpressionString, TemplateString?>
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            set.forEach { (expressionString, value) ->
                scope[expressionString.evaluate()] = value?.evaluate()?.let { MacroValue.Text(it) }
            }
        }

        companion object {
            operator fun invoke(vararg pairs: Pair<ExpressionString, TemplateString>) = Set(mapOf(*pairs))
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
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            if (condition.evaluate()) {
                then.run()
            } else {
                otherwise.run()
            }
        }
    }

    @Serializable
    @SerialName("when")
    data class When(
        @SerialName("when")
        val switch: TemplateString,
        val ignoreCase: Boolean = false,
        @SerialName("is")
        val cases: Map<TemplateString, MacroActions> = emptyMap(),
        @SerialName("else")
        val otherwise: MacroActions = MacroActions()
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            val switchValue = switch.evaluate()
            val actions = cases.asSequence().firstOrNull { (case, _) ->
                val caseValue = case.evaluate()
                switchValue.equals(caseValue, ignoreCase = ignoreCase)
            }?.value ?: otherwise
            actions.run()
        }
    }

    @Serializable
    @SerialName("print")
    data class Print(
        val print: TemplateString,
        val style: Style? = null, // TODO convert to StyleString
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

        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run(): Unit = macroTrace {
            if (debug && !scope.debugMode) return
            val message = print.evaluate()
            when (style) {
                null -> scope.println(message)
                Style.Info -> scope.info(message)
                Style.Success -> scope.success(message)
                Style.Warning -> scope.warning(message)
                Style.Error -> scope.danger(message)
            }
        }
    }

    @Serializable
    @SerialName("return")
    data class Return(
        @SerialName("return")
        @EncodeDefault(ALWAYS)
        val doReturn: Boolean = true,
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            if (doReturn) {
                scope.doReturn()
            }
        }
    }

    @Serializable
    @SerialName("exit")
    data class Exit(
        @EncodeDefault(ALWAYS)
        val exit: Boolean = true,
        val at: TemplateString? = null
    ) : MacroAction {
        context(scope: MacroCallScope, traceContext: MacroTraceContext)
        override fun run() = macroTrace {
            if (exit) {
                scope.controller.exit(atDirectory = at?.evaluateToAbsolutePathToDirectoryOrNull())
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
                    When.serializer(),
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
