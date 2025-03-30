@file:UseSerializers(KeyboardEventAsStringSerializer::class)
package de.jonasbroeckmann.nav

import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.utils.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Config(
    val editor: String = "nano",
    val hideHints: Boolean = false,
    val clearOnExit: Boolean = true,

    val limitToTerminalHeight: Boolean = true,
    val maxVisibleEntries: Int = 20,
    val maxVisiblePathElements: Int = 6,
    val inputTimeoutMillis: Int = 4,
    val suppressInitCheck: Boolean = false,

    val keys: Keys = Keys(),

    val colors: Colors = Colors.Retro,
    val modificationTime: ModificationTime = ModificationTime()
) {
    @Serializable
    data class Keys(
        val cursor: Cursor = Cursor(),
        val nav: Nav = Nav(),
        val menu: Menu = Menu(),
        val submit: KeyboardEvent = KeyboardEvent("Enter"),
        val cancel: KeyboardEvent = EscapeOrDelete,
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
        data class Menu(
            val up: KeyboardEvent = KeyboardEvent("PageUp"),
            val down: KeyboardEvent = KeyboardEvent("PageDown"),
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
            val clear: KeyboardEvent = EscapeOrDelete
        )
    }
    @Serializable
    data class Colors(
        val path: String = Retro.path,
        val filter: String = Retro.filter,
        val filterMarker: String = Retro.filterMarker,
        val keyHints: String = Retro.keyHints,

        val permissionRead: String = Retro.permissionRead,
        val permissionWrite: String = Retro.permissionWrite,
        val permissionExecute: String = Retro.permissionExecute,
        val entrySize: String = Retro.entrySize,
        val modificationTime: String = Retro.modificationTime,

        val directory: String = Retro.directory,
        val file: String = Retro.file,
        val link: String = Retro.link
    ) {
        companion object {
            @Suppress("unused")
            val Original = Colors(
                path = "1DFF7B",
                filter = "FFFFFF",
                filterMarker = "FF0000",
                keyHints = "FFFFFF",

                permissionRead = "C50F1F",
                permissionWrite = "13A10E",
                permissionExecute = "3B78FF",
                entrySize = "FFFF00",
                modificationTime = "00FF00",

                directory = "2FA2FF",
                file = "FFFFFF",
                link = "00FFFF"
            )
            val Retro = themed(
                main = "00DBB7",
                color1 = "F71674",
                color2 = "F5741D",
                color3 = "009FFD"
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
                keyHints = "FFFFFF",
                permissionRead = color1,
                permissionWrite = color2,
                permissionExecute = color3,
                entrySize = color2,
                modificationTime = color3,
                directory = color1,
                file = color2,
                link = color3
            )
        }
    }
    @Serializable
    data class ModificationTime(
        val minimumBrightness: Double = 0.4,
        val halfBrightnessAtHours: Double = 12.0
    )

    companion object {
        val DefaultPath by lazy { UserHome / ".config" / "nav.toml" }
        const val ENV_VAR_NAME = "NAV_CONFIG"

        fun load(terminal: Terminal): Config {
            val path = getenv(ENV_VAR_NAME) // if specified explicitly don't check for existence
                ?: DefaultPath.takeIf { it.exists() }?.toString()
                ?: return Config()
            try {
                return TomlFileReader.decodeFromFile(serializer(), path)
            } catch (e: Exception) {
                terminal.danger("Could not load config: ${e.message}")
                terminal.info("Using default config")
                return Config()
            }
        }

        private val EscapeOrDelete get() = KeyboardEvent("Escape")
    }
}
