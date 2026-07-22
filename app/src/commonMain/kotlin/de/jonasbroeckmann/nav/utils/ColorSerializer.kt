package de.jonasbroeckmann.nav.utils

import com.github.ajalt.colormath.Color
import com.github.ajalt.colormath.model.RGBInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorSerializer : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor(ColorSerializer::class.qualifiedName!!, LONG)

    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeLong(value.toSRGB().toRGBInt().argb.toLong())

    override fun deserialize(decoder: Decoder): Color = RGBInt(decoder.decodeLong().toUInt())
}
