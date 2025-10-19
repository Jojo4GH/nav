package de.jonasbroeckmann.nav.utils

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KeyboardEventAsStringSerializer : KSerializer<KeyboardEvent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(KeyboardEventAsStringSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: KeyboardEvent) = encoder.encodeString(
        buildList {
            if (value.ctrl) add("ctrl")
            if (value.shift) add("shift")
            if (value.alt) add("alt")
            add(value.key)
        }.joinToString(MODIFIER_SEPARATOR)
    )

    override fun deserialize(decoder: Decoder): KeyboardEvent = decoder.decodeString().split(MODIFIER_SEPARATOR).let { parts ->
        val key = parts.last()
        val modifiers = parts.dropLast(1).map { it.lowercase() }
        KeyboardEvent(
            key = key,
            ctrl = "ctrl" in modifiers,
            shift = "shift" in modifiers,
            alt = "alt" in modifiers
        )
    }

    private const val MODIFIER_SEPARATOR = "+"
}
