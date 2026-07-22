@file:UseSerializers(TextStyleSerializer::class)

package de.jonasbroeckmann.nav.config

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.utils.TextStyleSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.reflect.KProperty0

@Serializable
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

    val success: TextStyle = TextColors.rgb("#66c322"),
    val danger: TextStyle = TextColors.rgb("#ff2a00"),
    val warning: TextStyle = TextColors.rgb("#ffcc00"),
    val info: TextStyle = TextColors.rgb("#7ecefc"),
    val debugStyle: TextStyle = TextColors.magenta
) : StylesProvider, Map<String, TextStyle> {
    override val styles get() = this

    private val map by lazy {
        mapOf(
            entry(::path),
            entry(::filter),
            entry(::filterMarker),
            entry(::keyHints),
            entry(::keyHintLabels),
            entry(::genericElements),
            entry(::permissionRead),
            entry(::permissionWrite),
            entry(::permissionExecute),
            entry(::permissionHeader),
            entry(::hardlinkCount),
            entry(::hardlinkCountHeader),
            entry(::user),
            entry(::userHeader),
            entry(::group),
            entry(::groupHeader),
            entry(::entrySize),
            entry(::entrySizeHeader),
            entry(::modificationTime),
            entry(::modificationTimeHeader),
            entry(::directory),
            entry(::file),
            entry(::link),
            entry(::nameHeader),
            entry(::nameDecorations),
            entry(::success),
            entry(::danger),
            entry(::warning),
            entry(::info),
            entry(::debugStyle),
        )
    }

    override val size get() = map.size
    override val entries get() = map.entries
    override val keys get() = map.keys
    override val values get() = map.values

    override fun get(key: String) = map[key]

    override fun containsKey(key: String) = map.containsKey(key)

    override fun containsValue(value: TextStyle) = map.containsValue(value)

    override fun isEmpty() = map.isEmpty()
}

private fun <T> entry(property: KProperty0<T>) = property.name to property.get()
