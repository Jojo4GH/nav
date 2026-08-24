package de.jonasbroeckmann.nav.command

import com.github.ajalt.mordant.rendering.AnsiLevel
import de.jonasbroeckmann.nav.config.Config

interface CommandOptions {
    val showHiddenEntries: Boolean?
    val configPath: String?
    val editConfig: Boolean
    val editor: String?
    val forceAnsiLevel: AnsiLevel?
    val renderMode: RenderModeOption
    val shell: Shell?

    enum class RenderModeOption(
        val label: String,
        val accessibility: Config.Accessibility,
        val forceNoColor: Boolean = false
    ) {
        Auto("auto", Config.Accessibility()),
        Simple("simple", Config.Accessibility(simpleColors = true)),
        Accessible("accessible", Config.Accessibility(decorations = true)),
        SimpleAccessible("simple-accessible", Config.Accessibility(simpleColors = true, decorations = true)),
        NoColor("no-color", Config.Accessibility(simpleColors = true, decorations = true), forceNoColor = true)
    }

    companion object {
        operator fun invoke(
            showHiddenEntries: Boolean? = null,
            configPath: String? = null,
            editConfig: Boolean = false,
            editor: String? = null,
            forceAnsiLevel: AnsiLevel? = null,
            renderMode: RenderModeOption = RenderModeOption.Auto,
            shell: Shell? = null,
        ) = object : CommandOptions {
            override val showHiddenEntries = showHiddenEntries
            override val configPath = configPath
            override val editConfig = editConfig
            override val editor = editor
            override val forceAnsiLevel = forceAnsiLevel
            override val renderMode = renderMode
            override val shell = shell
        }
    }
}
