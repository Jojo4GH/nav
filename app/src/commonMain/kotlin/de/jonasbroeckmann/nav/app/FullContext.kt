package de.jonasbroeckmann.nav.app

import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroProvider
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.config.Styles
import de.jonasbroeckmann.nav.config.StylesProvider

interface FullContext : PartialContext, ConfigProvider, StylesProvider, MacroProvider {
    val editorCommand: String?

    override val styles: Styles

    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean

    val identifiedMacros: Map<String, Macro>

    override fun macroById(id: String) = identifiedMacros[id]
}

context(context: FullContext)
val context get() = context
