package de.jonasbroeckmann.nav.config

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle

data class Styles(
    val path: TextStyle,
    val filter: TextStyle,
    val filterMarker: TextStyle,
    val keyHints: TextStyle,
    val keyHintLabels: TextStyle,
    val genericElements: TextStyle,

    val permissionRead: TextStyle,
    val permissionWrite: TextStyle,
    val permissionExecute: TextStyle,
    val permissionHeader: TextStyle,
    val hardlinkCount: TextStyle,
    val hardlinkCountHeader: TextStyle,
    val user: TextStyle,
    val userHeader: TextStyle,
    val group: TextStyle,
    val groupHeader: TextStyle,
    val entrySize: TextStyle,
    val entrySizeHeader: TextStyle,
    val modificationTime: TextStyle,
    val modificationTimeHeader: TextStyle,

    val directory: TextStyle,
    val file: TextStyle,
    val link: TextStyle,
    val nameHeader: TextStyle,
    val nameDecorations: TextStyle,

    val debugStyle: TextStyle = TextColors.magenta
) : StylesProvider {
    override val styles get() = this
}
