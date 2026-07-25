@file:UseSerializers(ColorSerializer::class)

package de.jonasbroeckmann.nav.utils

import com.github.ajalt.colormath.Color
import com.github.ajalt.mordant.rendering.TextStyle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TextStyleSerializer : KSerializer<TextStyle> {
    private val delegate = SerializableTextStyle.serializer()

    override val descriptor = SerialDescriptor(TextStyleSerializer::class.qualifiedName!!, delegate.descriptor)

    override fun serialize(encoder: Encoder, value: TextStyle) = encoder.encodeSerializableValue(delegate, SerializableTextStyle(value))

    override fun deserialize(decoder: Decoder): TextStyle = decoder.decodeSerializableValue(delegate)

    @Serializable
    private data class SerializableTextStyle(
        override val color: Color?,
        override val bgColor: Color?,
        override val bold: Boolean?,
        override val italic: Boolean?,
        override val underline: Boolean?,
        override val dim: Boolean?,
        override val inverse: Boolean?,
        override val strikethrough: Boolean?,
        override val hyperlink: String?,
        override val hyperlinkId: String?,
    ) : TextStyle {
        constructor(style: TextStyle) : this(
            color = style.color,
            bgColor = style.bgColor,
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            dim = style.dim,
            inverse = style.inverse,
            strikethrough = style.strikethrough,
            hyperlink = style.hyperlink,
            hyperlinkId = style.hyperlinkId,
        )

        override val bg get() = copy(color = null, bgColor = color)

        override infix fun on(bg: TextStyle) = copy(bgColor = bg.color)
    }
}
