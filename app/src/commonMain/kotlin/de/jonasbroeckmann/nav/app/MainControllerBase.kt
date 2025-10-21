package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.which

abstract class MainControllerBase internal constructor() : MainController {
    override val editorCommand by lazy {
        // override editor from command line argument or config or fill in default editor
        context.command.configurationOptions.editor
            ?.also { context.printlnOnDebug { "Using editor from command line argument: $it" } }
            ?: config.editor
            ?: findDefaultEditorCommand()
    }

    override val styles by lazy {
        // override from command line argument or config or fill in based on terminal capabilities
        val useSimpleColors = command.configurationOptions.renderMode.accessibility.simpleColors
            ?: config.accessibility.simpleColors
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256 -> false
                ANSI16, NONE -> true
            }
        config.partialColors filledWith when (useSimpleColors) {
            true -> config.partialColors.simpleTheme.styles
            false -> config.partialColors.theme.styles
        }
    }

    override val accessibilitySimpleColors by lazy {
        command.configurationOptions.renderMode.accessibility.simpleColors
            ?: config.accessibility.simpleColors
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256 -> false
                ANSI16, NONE -> true
            }
    }

    override val accessibilityDecorations by lazy {
        command.configurationOptions.renderMode.accessibility.decorations
            ?: config.accessibility.decorations
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256, ANSI16 -> false
                NONE -> true
            }
    }

    override val identifiedMacros by lazy {
        config.macros
            .mapNotNull { macro -> macro.id?.let { it to macro } }
            .toMap()
    }

    companion object {
        private val DefaultEditorPrograms = listOf("nano", "nvim", "vim", "vi", "code", "notepad")

        context(context: PartialContext)
        private fun findDefaultEditorCommand(): String? {
            context.printlnOnDebug { "Searching for default editor:" }

            fun checkEnvVar(name: String): String? {
                val value = getEnvironmentVariable(name)?.trim() ?: run {
                    context.printlnOnDebug { $$"  $$$name not set" }
                    return null
                }
                if (value.isBlank()) {
                    context.printlnOnDebug { $$"  $$$name is empty" }
                    return null
                }
                context.printlnOnDebug { $$"  Using value of $$$name: $$value" }
                return value
            }

            fun checkProgram(name: String): String? {
                val path = which(name) ?: run {
                    context.printlnOnDebug { $$"  $$name not found in $PATH" }
                    return null
                }
                context.printlnOnDebug { "  Found $name at $path" }
                return "\"$path\"" // quote path to handle spaces
            }

            return sequence {
                yield(checkEnvVar("EDITOR"))
                yield(checkEnvVar("VISUAL"))
                DefaultEditorPrograms.forEach { name ->
                    yield(checkProgram(name))
                }
            }.filterNotNull().firstOrNull().also {
                if (it == null) {
                    context.terminal.danger("Could not find a default editor")
                    context.terminal.warning(specifyEditorMessage)
                }
            }
        }

        private val specifyEditorMessage: String get() {
            return $$"""Please specify an editor via the --editor CLI option, the config file or the $EDITOR environment variable"""
        }
    }
}
