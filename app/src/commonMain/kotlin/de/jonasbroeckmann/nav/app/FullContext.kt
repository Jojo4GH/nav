package de.jonasbroeckmann.nav.app

import de.jonasbroeckmann.nav.app.macros.MacroProvider
import de.jonasbroeckmann.nav.app.macros.MacroProvider.Companion.onLoaded
import de.jonasbroeckmann.nav.app.macros.components.DefaultMacro
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.context.MacroSessionContext
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.config.Styles
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.printlnOnDebug
import de.jonasbroeckmann.nav.utils.EnvironmentVariables
import de.jonasbroeckmann.nav.utils.which
import kotlinx.serialization.encodeToString

interface FullContext : PartialContext, ConfigProvider, StylesProvider, MacroProvider {
    val editorCommand: String?

    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean

    companion object {
        context(partialContext: PartialContext, configProvider: ConfigProvider)
        operator fun invoke(): FullContext = FullContextImpl(partialContext, configProvider)
    }
}

context(context: FullContext)
val context get() = context

private class FullContextImpl(
    partialContext: PartialContext,
    configProvider: ConfigProvider
) : FullContext,
    PartialContext by partialContext,
    ConfigProvider by configProvider,
    MacroProvider by MacroProvider(DefaultMacro.macros + configProvider.config.macros).onLoaded({
        partialContext.printlnOnDebug { "\nLoaded ${it.size} macros:" }
        partialContext.printlnOnDebug { Config.Yaml.encodeToString(it) + "\n" }
    })
{
    override val editorCommand by lazy {
        // override editor from command line argument or config or fill in default editor
        commandOptions.editor
            ?.also { printlnOnDebug { "Using editor from command line argument: $it" } }
            ?: config.editor
            ?: findDefaultEditorCommand()
    }

    override val styles by lazy {
        // override from command line argument or config or fill in based on terminal capabilities
        val useSimpleColors = commandOptions.renderMode.accessibility.simpleColors
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
        commandOptions.renderMode.accessibility.simpleColors
            ?: config.accessibility.simpleColors
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256 -> false
                ANSI16, NONE -> true
            }
    }

    override val accessibilityDecorations by lazy {
        commandOptions.renderMode.accessibility.decorations
            ?: config.accessibility.decorations
            ?: when (terminal.terminalInfo.ansiLevel) {
                TRUECOLOR, ANSI256, ANSI16 -> false
                NONE -> true
            }
    }

    companion object {
        private val DefaultEditorPrograms = listOf("nano", "nvim", "vim", "vi", "code", "notepad")

        context(context: PartialContext)
        private fun findDefaultEditorCommand(): String? {
            context.printlnOnDebug { "Searching for default editor:" }

            fun checkEnvVar(name: String): String? {
                val value = EnvironmentVariables[name]?.trim() ?: run {
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
                    context.danger("Could not find a default editor")
                    context.warning(specifyEditorMessage)
                }
            }
        }

        private val specifyEditorMessage: String get() {
            return $$"""Please specify an editor via the --editor CLI option, the config file or the $EDITOR environment variable"""
        }
    }
}
