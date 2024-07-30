package de.jonasbroeckmann.nav.utils

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object KeyboardEventAsStringSerializer : KSerializer<KeyboardEvent> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("KeyboardEvent", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: KeyboardEvent) {
        encoder.encodeString(listOfNotNull(
            "ctrl".takeIf { value.ctrl },
            "shift".takeIf { value.shift },
            "alt".takeIf { value.alt },
            value.key
        ).joinToString("+"))
    }
    override fun deserialize(decoder: Decoder): KeyboardEvent {
        decoder.decodeString().split("+").let {
            val key = it.last()
            val modifiers = it.dropLast(1)
            return KeyboardEvent(
                key = key,
                ctrl = "ctrl" in modifiers,
                shift = "shift" in modifiers,
                alt = "alt" in modifiers
            )
        }
    }
}