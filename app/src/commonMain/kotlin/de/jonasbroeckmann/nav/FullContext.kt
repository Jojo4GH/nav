package de.jonasbroeckmann.nav

interface FullContext : PartialContext, ConfigProvider {
    val editorCommand: String?

    val styles: Styles

    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean
}
