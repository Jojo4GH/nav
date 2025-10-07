package de.jonasbroeckmann.nav.app

import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.config.Styles

interface FullContext : PartialContext, ConfigProvider {
    val editorCommand: String?

    val styles: Styles

    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean

    val namedMacros: Map<String, Macro>
}

context(context: FullContext)
val context get() = context
