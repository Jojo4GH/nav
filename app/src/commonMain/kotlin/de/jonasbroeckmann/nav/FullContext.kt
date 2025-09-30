package de.jonasbroeckmann.nav

interface FullContext : PartialContext, ConfigProvider {
    val editorCommand: String?
    val colors: Colors
    val accessibilitySimpleColors: Boolean
    val accessibilityDecorations: Boolean
}
