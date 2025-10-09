package de.jonasbroeckmann.nav.config

import com.github.ajalt.mordant.rendering.TextStyle

data class Styleables<T>(
    val path: T,
    val filter: T,
    val filterMarker: T,
    val keyHints: T,
    val keyHintLabels: T,
    val genericElements: T,

    val permissionRead: T,
    val permissionWrite: T,
    val permissionExecute: T,
    val permissionHeader: T,
    val hardlinkCount: T,
    val hardlinkCountHeader: T,
    val user: T,
    val userHeader: T,
    val group: T,
    val groupHeader: T,
    val entrySize: T,
    val entrySizeHeader: T,
    val modificationTime: T,
    val modificationTimeHeader: T,

    val directory: T,
    val file: T,
    val link: T,
    val nameHeader: T,
    val nameDecorations: T,
) : StyleablesProvider<T> {
    override val styles get() = this
}

interface StyleablesProvider<T> {
    val styles: Styleables<T>
}

context(styleablesProvider: StyleablesProvider<T>)
val <T> styles get() = styleablesProvider.styles

typealias Styles = Styleables<TextStyle>

typealias StylesProvider = StyleablesProvider<TextStyle>
