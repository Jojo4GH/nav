package de.jonasbroeckmann.nav.framework.context

import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.config.Styles

interface FullContext : PartialContext, ConfigProvider, StylesProvider {
    val editorCommand: String?

    override val styles: Styles

    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean

    val identifiedMacros: Map<String, Macro>
}

context(context: FullContext)
val context get() = context
