package de.jonasbroeckmann.nav.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RegexAsStringSerializer : KSerializer<Regex> {
    override val descriptor get() = PrimitiveSerialDescriptor(Regex::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Regex) = encoder.encodeString(value.pattern)

    override fun deserialize(decoder: Decoder) = Regex(decoder.decodeString())
}
