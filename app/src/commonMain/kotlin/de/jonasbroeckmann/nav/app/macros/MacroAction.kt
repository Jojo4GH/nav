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
import de.jonasbroeckmann.nav.app.openInEditor
import de.jonasbroeckmann.nav.app.runCommand
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultChoicePrompt
import de.jonasbroeckmann.nav.app.ui.dialogs.defaultTextPrompt
import de.jonasbroeckmann.nav.command.dangerOnDebug
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.RegexAsStringSerializer
import kotlinx.io.files.Path
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
        val resultTo: String = "result"
    ) : MacroAction {
        init {
            require(listOfNotNull(format, choices.takeIf { it.isNotEmpty() }).size <= 1) {
                "Only one of '${::format.name}' or '${::choices.name}' can be set"
            }
        }

        context(context: MacroRuntimeContext)
        override fun run() {
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
                context.dangerOnDebug { "Aborting macro because prompt was cancelled." }
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
        context(context: MacroRuntimeContext)
        override fun run() {
            val macroId = macro.evaluate()
            val macro = context.identifiedMacros[macroId]
                ?: throw IllegalArgumentException("No macro with ${Macro::id.name} '$macroId' found")

            context.call(
                parameters = parameters?.mapKeys { (name, _) -> MacroSymbol.fromString(name) },
                capture = capture?.mapKeys { (name, _) -> MacroSymbol.fromString(name) },
                returnBarrier = continueOnReturn
            ) {
                MacroRunnable {
                    if (ignoreCondition || macro.available()) {
                        macro.run()
                    } else {
                        contextOf<MacroRuntimeContext>().printlnOnDebug {
                            "Skipping macro '$macroId' because its condition was not met."
                        }
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
        context(context: MacroRuntimeContext)
        override fun run() {
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
        val exitCodeTo: String = DefaultMacroSymbols.ExitCode.name
    ) : MacroAction {
        context(context: MacroRuntimeContext)
        override fun run() {
            val exitCode = openInEditor(Path(open.evaluate()))
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
            @SerialName("info")
            Info,

            @SerialName("success")
            Success,

            @SerialName("warning")
            Warning,

            @SerialName("error")
            Error,
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
                exit(at?.evaluate()?.parseAbsolutePathToDirectoryOrNull())
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
