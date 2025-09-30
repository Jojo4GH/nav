@file:UseSerializers(KeyboardEventAsStringSerializer::class)

package de.jonasbroeckmann.nav

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.EntryColumn
import de.jonasbroeckmann.nav.app.EntryColumn.*
import de.jonasbroeckmann.nav.app.State
import de.jonasbroeckmann.nav.utils.*
import kotlinx.io.files.Path
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Config private constructor(
    val editor: String? = null,
    val hideHints: Boolean = false,
    val clearOnExit: Boolean = true,

    val limitToTerminalHeight: Boolean = true,
    val maxVisibleEntries: Int = 20,
    val maxVisiblePathElements: Int = 6,
    val inputTimeoutMillis: Int = 4,
    val suppressInitCheck: Boolean = false,

    val shownColumns: List<EntryColumn> = listOf(
        Permissions,
        // HardLinkCount,
        // UserName,
        // GroupName,
        EntrySize,
        LastModified,
    ),

    val keys: Keys = Keys(),

    @SerialName("colors")
    val partialColors: PartialColors = PartialColors(),
    val accessibility: Accessibility = Accessibility(),
    val autocomplete: Autocomplete = Autocomplete(),
    val modificationTime: ModificationTime = ModificationTime(),

    val entryMacros: List<EntryMacro> = emptyList()
) : ConfigProvider {
    override val config get() = this

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
    data class PartialColors(
        val theme: Theme = Retro,
        val simpleTheme: Theme = Monochrome,

        val path: String? = null,
        val filter: String? = null,
        val filterMarker: String? = null,
        val keyHints: String? = null,

        val permissionRead: String? = null,
        val permissionWrite: String? = null,
        val permissionExecute: String? = null,
        val hardlinkCount: String? = null,
        val user: String? = null,
        val group: String? = null,
        val entrySize: String? = null,
        val modificationTime: String? = null,

        val directory: String? = null,
        val file: String? = null,
        val link: String? = null
    ) {
        infix fun filledWith(colors: Colors): Colors = Colors(
            path = this.path ?: colors.path,
            filter = this.filter ?: colors.filter,
            filterMarker = this.filterMarker ?: colors.filterMarker,
            keyHints = this.keyHints ?: colors.keyHints,
            permissionRead = this.permissionRead ?: colors.permissionRead,
            permissionWrite = this.permissionWrite ?: colors.permissionWrite,
            permissionExecute = this.permissionExecute ?: colors.permissionExecute,
            hardlinkCount = this.hardlinkCount ?: colors.hardlinkCount,
            user = this.user ?: colors.user,
            group = this.group ?: colors.group,
            entrySize = this.entrySize ?: colors.entrySize,
            modificationTime = this.modificationTime ?: colors.modificationTime,
            directory = this.directory ?: colors.directory,
            file = this.file ?: colors.file,
            link = this.link ?: colors.link
        )

        @Suppress("unused")
        @Serializable
        enum class Theme(val colors: Colors) {
            /**
             * The default theme with a retro look.
             */
            Retro(themed(
                main = "00DBB7",
                color1 = "F71674",
                color2 = "F5741D",
                color3 = "009FFD"
            )),
            /**
             * Only monochrome colors.
             */
            Monochrome(themed(
                main = "FFFFFF",
                color1 = "FFFFFF",
                color2 = "FFFFFF",
                color3 = "FFFFFF"
            )),
            /**
             * A simple theme supporting even basic 16 color terminals.
             */
            SimpleColor(themed(
                main = "00FF00",   // bright green
                color1 = "FF00FF", // bright magenta
                color2 = "FFFF00", // bright yellow
                color3 = "00FFFF", // bright cyan
            )),
            Original(Colors(
                path = "1DFF7B",
                filter = "FFFFFF",
                filterMarker = "FF0000",
                keyHints = "FFFFFF",

                permissionRead = "C50F1F",
                permissionWrite = "13A10E",
                permissionExecute = "3B78FF",
                hardlinkCount = "13A10E",
                user = "C50F1F",
                group = "C50F1F",
                entrySize = "FFFF00",
                modificationTime = "00FF00",

                directory = "2FA2FF",
                file = "FFFFFF",
                link = "00FFFF"
            ))
        }

        companion object {
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
                hardlinkCount = color2,
                user = color1,
                group = color1,
                entrySize = color2,
                modificationTime = color3,
                directory = color1,
                file = color2,
                link = color3
            )
        }
    }

    @Serializable
    data class Accessibility(
        val decorations: Boolean? = null,
        val simpleColors: Boolean? = null
    )

    @Serializable
    data class Autocomplete(
        val style: Style = Style.CommonPrefixCycle,
        val autoNavigation: AutoNavigation = AutoNavigation.OnSingleAfterCompletion,
    ) {
        @Serializable
        enum class Style {
            /** Auto complete the largest common prefix and stop */
            CommonPrefixStop,

            /** Auto complete the largest common prefix and cycle through all entries */
            CommonPrefixCycle
        }

        @Serializable
        enum class AutoNavigation {
            /** Do not auto navigate */
            None,

            /** Auto complete the entry and on second action navigate */
            OnSingleAfterCompletion,

            /** Auto complete the entry and navigate immediately (not recommended) */
            OnSingle
        }
    }

    @Serializable
    data class ModificationTime(
        val minimumBrightness: Double = 0.4,
        val halfBrightnessAtHours: Double = 12.0
    )

    @Serializable
    data class EntryMacro(
        /** Allows placeholders */
        val description: String,
        val onFile: Boolean = false,
        val onDirectory: Boolean = false,
        val onSymbolicLink: Boolean = false,
        /** Allows placeholders */
        val command: String,
        val afterCommand: AfterMacroCommand = AfterMacroCommand.DoNothing,
        val afterSuccessfulCommand: AfterMacroCommand = afterCommand,
        val afterFailedCommand: AfterMacroCommand = afterCommand,
        val quickMacroKey: KeyboardEvent? = null
    ) {
        context(state: State, configProvider: ConfigProvider)
        fun computeDescription(
            currentEntry: Entry
        ) = description.replacePlaceholders(
            state = state,
            currentEntry = currentEntry
        )

        context(state: State)
        fun computeCommand(
            currentEntry: Entry
        ) = command.replacePlaceholders(
            state = state,
            currentEntry = currentEntry
        )

        private fun String.replacePlaceholders(
            state: State,
            currentEntry: Entry
        ) = this
            .replace(PLACEHOLDER_INITIAL_DIR, WorkingDirectory.toString())
            .replace(PLACEHOLDER_DIR, state.directory.toString())
            .replace(PLACEHOLDER_ENTRY_PATH, currentEntry.path.toString())
            .replace(PLACEHOLDER_ENTRY_NAME, currentEntry.path.name)
            .replace(PLACEHOLDER_FILTER, state.filter)

        companion object {
            private const val PLACEHOLDER_INITIAL_DIR = "{initialDir}"
            private const val PLACEHOLDER_DIR = "{dir}"
            private const val PLACEHOLDER_ENTRY_PATH = "{entryPath}"
            private const val PLACEHOLDER_ENTRY_NAME = "{entryName}"
            private const val PLACEHOLDER_FILTER = "{filter}"
        }
    }

    @Serializable
    enum class AfterMacroCommand {
        DoNothing,
        ExitAtCurrentDirectory,
        ExitAtInitialDirectory,
    }

    companion object {
        val DefaultPath by lazy { UserHome / ".config" / "nav.toml" }
        const val ENV_VAR_NAME = "NAV_CONFIG"

        context(context: PartialContext)
        fun findExplicitPath(): Path? = context.command.configurationOptions.configPath?.let { Path(it) }
            ?: getenv(ENV_VAR_NAME)?.takeUnless { it.isBlank() }?.let { Path(it) }

        context(context: PartialContext)
        fun load(): Config {
            try {
                val explicitPath = findExplicitPath()?.also {
                    require(it.exists()) { "The specified config does not exist: $it" }
                    require(it.isRegularFile) { "The specified config is not a file: $it" }
                }
                val path = when {
                    explicitPath != null -> explicitPath
                    DefaultPath.exists() && DefaultPath.isRegularFile -> DefaultPath
                    else -> return Config()
                }
                return TomlFileReader(
                    inputConfig = TomlInputConfig(
                        ignoreUnknownNames = true
                    ),
                    outputConfig = TomlOutputConfig()
                ).decodeFromFile(
                    deserializer = serializer(),
                    tomlFilePath = path.toString()
                )
            } catch (e: Exception) {
                context.dangerThrowable(e, "Could not load config: ${e.message}")
                context.terminal.warning("Using default config")
                return Config()
            }
        }

        private val EscapeOrDelete get() = KeyboardEvent("Escape")
    }
}

interface ConfigProvider {
    val config: Config
}
