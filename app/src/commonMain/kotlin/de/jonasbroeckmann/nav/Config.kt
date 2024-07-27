@file:UseSerializers(KeyboardEventAsStringSerializer::class)
package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable
data class Config(
    val maxVisibleEntries: Int = 32,
    val maxVisiblePathElements: Int = 6,
    val hideHints: Boolean = false,
    val clearOnExit: Boolean = true,
    val editor: String = "nano",

    val keys: Keys = Keys(),

    val colors: Colors = Colors.Retro
) {
    @Serializable
    data class Keys(
        val cursor: Cursor = Cursor(),
        val nav: Nav = Nav(),
        val submit: KeyboardEvent = KeyboardEvent("Enter"),
        val cancel: KeyboardEvent = KeyboardEvent("Escape"),
        val filter: Filter = Filter()
    ) {
        @Serializable
        data class Cursor(
            val up: KeyboardEvent = KeyboardEvent("ArrowUp"),
            val down: KeyboardEvent = KeyboardEvent("ArrowDown"),
            val home: KeyboardEvent = KeyboardEvent("Home"),
            val end: KeyboardEvent = KeyboardEvent("End")
        )
        @Serializable
        data class Nav(
            val up: KeyboardEvent = KeyboardEvent("ArrowLeft"),
            val into: KeyboardEvent = KeyboardEvent("ArrowRight"),
            val open: KeyboardEvent = KeyboardEvent("ArrowRight")
        )
        @Serializable
        data class Filter(
            val autocomplete: KeyboardEvent = KeyboardEvent("Tab"),
            val clear: KeyboardEvent = KeyboardEvent("Escape")
        )
    }
    @Serializable
    data class Colors(
        val path: String = "1dff7b",
        val filter: String = "ffffff",
        val filterMarker: String = "ff0000",
        val keyHints: String = "ffffff",

        val permissionRead: String = "c50f1f",
        val permissionWrite: String = "13a10e",
        val permissionExecute: String = "3b78ff",
        val entrySize: String = "ffff00",
        val modificationTime: String = "00ff00",

        val directory: String = "2fa2ff",
        val file: String = "ffffff",
        val link: String = "00ffff",

    ) {
        companion object {
            @Suppress("unused")
            val Original = Colors(
                path = "1dff7b",
                filter = "ffffff",
                filterMarker = "ff0000",
                keyHints = "ffffff",

                permissionRead = "c50f1f",
                permissionWrite = "13a10e",
                permissionExecute = "3b78ff",
                entrySize = "ffff00",
                modificationTime = "00ff00",

                directory = "2fa2ff",
                file = "ffffff",
                link = "00ffff"
            )

            fun themed(
                main: String,
                color1: String,
                color2: String,
                color3: String
            ) = Colors(
                path = main,
                filter = main,
                filterMarker = main,
                permissionRead = color1,
                permissionWrite = color2,
                permissionExecute = color3,
                entrySize = color2,
                modificationTime = color3,
                directory = color1,
                file = color2,
                link = color3
            )

            val Retro = themed(
                main = "00DBB7",
                color1 = "F71674",
                color2 = "F5741D",
                color3 = "009FFD"
            )
        }
    }
}

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