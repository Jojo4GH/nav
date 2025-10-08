@file:UseSerializers(KeyboardEventAsStringSerializer::class)

package de.jonasbroeckmann.nav.config

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.EntryColumn
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.*
import kotlinx.io.files.Path
import kotlinx.io.okio.asOkioSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class Config private constructor(
    val editor: String? = null,
    val hideHints: Boolean = false,
    val clearOnExit: Boolean = true,

    val limitToTerminalHeight: Boolean = true,
    val maxVisibleEntries: Int = 40,
    val maxVisiblePathElements: Int = 6,
    val showHiddenEntries: Boolean = true,
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

    val entryMacros: List<EntryMacro> = emptyList(),
    val macros: List<Macro> = emptyList(),
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
        val keyHintLabels: String? = null,
        val genericElements: String? = null,

        val permissionRead: String? = null,
        val permissionWrite: String? = null,
        val permissionExecute: String? = null,
        val permissionHeader: String? = null,
        val hardlinkCount: String? = null,
        val hardlinkCountHeader: String? = null,
        val user: String? = null,
        val userHeader: String? = null,
        val group: String? = null,
        val groupHeader: String? = null,
        val entrySize: String? = null,
        val entrySizeHeader: String? = null,
        val modificationTime: String? = null,
        val modificationTimeHeader: String? = null,

        val directory: String? = null,
        val file: String? = null,
        val link: String? = null,
        val nameHeader: String? = null,
        val nameDecorations: String? = null,
    ) {
        @Suppress("detekt:CyclomaticComplexMethod")
        infix fun filledWith(styles: Styles): Styles = Styles(
            path = path?.parseColor() ?: styles.path,
            filter = filter?.parseColor() ?: styles.filter,
            filterMarker = filterMarker?.parseColor() ?: styles.filterMarker,
            keyHints = keyHints?.parseColor() ?: styles.keyHints,
            keyHintLabels = keyHintLabels?.parseColor() ?: styles.keyHintLabels,
            genericElements = genericElements?.parseColor() ?: styles.genericElements,
            permissionRead = permissionRead?.parseColor() ?: styles.permissionRead,
            permissionWrite = permissionWrite?.parseColor() ?: styles.permissionWrite,
            permissionExecute = permissionExecute?.parseColor() ?: styles.permissionExecute,
            permissionHeader = permissionHeader?.parseColor() ?: styles.permissionHeader,
            hardlinkCount = hardlinkCount?.parseColor() ?: styles.hardlinkCount,
            hardlinkCountHeader = hardlinkCountHeader?.parseColor() ?: styles.hardlinkCountHeader,
            user = user?.parseColor() ?: styles.user,
            userHeader = userHeader?.parseColor() ?: styles.userHeader,
            group = group?.parseColor() ?: styles.group,
            groupHeader = groupHeader?.parseColor() ?: styles.groupHeader,
            entrySize = entrySize?.parseColor() ?: styles.entrySize,
            entrySizeHeader = entrySizeHeader?.parseColor() ?: styles.entrySizeHeader,
            modificationTime = modificationTime?.parseColor() ?: styles.modificationTime,
            modificationTimeHeader = modificationTimeHeader?.parseColor() ?: styles.modificationTimeHeader,
            directory = directory?.parseColor() ?: styles.directory,
            file = file?.parseColor() ?: styles.file,
            link = link?.parseColor() ?: styles.link,
            nameHeader = nameHeader?.parseColor() ?: styles.nameHeader,
            nameDecorations = nameDecorations?.parseColor() ?: styles.nameDecorations,
        )

        @Suppress("unused")
        @Serializable
        enum class Theme(val styles: Styles) {
            Retro(Themes.Retro),
            Monochrome(Themes.Monochrome),
            SimpleColor(Themes.SimpleColor),
            Random(Themes.Random),
            Sunset(Themes.Sunset),
            Xmas(Themes.Xmas),
            Hub(Themes.Hub),
            Ice(Themes.Ice),
            Darcula(Themes.Darcula),
            AtomOneDark(Themes.AtomOneDark),
            HackerHacker(Themes.HackerHacker),
        }

        companion object {
            private fun String.parseColor() = rgb(this)
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
        context(state: State)
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
        val DefaultPaths by lazy {
            listOf(
                UserHome / ".config" / "nav.toml",
                UserHome / ".config" / "nav.yaml",
                UserHome / ".config" / "nav.yml",
            )
        }
        const val ENV_VAR_NAME = "NAV_CONFIG"

        context(context: PartialContext)
        fun findExplicitPath(): Path? = context.command.configurationOptions.configPath?.let { Path(it) }
            ?: getEnvironmentVariable(ENV_VAR_NAME)?.takeUnless { it.isBlank() }?.let { Path(it) }

        fun findDefaultPath(mustExist: Boolean = true): Path? {
            val firstExiting = DefaultPaths.firstOrNull { it.exists() && it.isRegularFile }
            return if (mustExist) {
                firstExiting
            } else {
                firstExiting ?: DefaultPaths.firstOrNull { !it.exists() || it.isRegularFile }
            }
        }

        context(context: PartialContext)
        fun load(): Config {
            fun errorOnLoad(e: Exception, message: Any?): Config {
                context.dangerThrowable(e, "Could not load config: $message")
                context.terminal.warning("Using default config")
                return Config()
            }
            try {
                val explicitPath = findExplicitPath()?.also {
                    require(it.exists()) { "The specified config does not exist: $it" }
                    require(it.isRegularFile) { "The specified config is not a file: $it" }
                }
                val path = explicitPath
                    ?: findDefaultPath(mustExist = true)
                    ?: run {
                        context.printlnOnDebug { "Could not find config, using default" }
                        return Config()
                    }
                val (_, extension) = path.nameAndExtension
                when (extension?.lowercase()) {
                    "toml" -> return loadFromToml(path)
                    "yaml", "yml" -> return loadFromYaml(path)
                    else -> {
                        context.terminal.danger("Could not determine type of config file for: $path")
                        context.terminal.warning("Using default config")
                        return Config()
                    }
                }
            } catch (e: YamlException) {
                return errorOnLoad(e, e)
            } catch (e: Exception) {
                return errorOnLoad(e, e.message)
            }
        }

        private fun loadFromToml(path: Path) = TomlFileReader(
            inputConfig = TomlInputConfig(
                ignoreUnknownNames = true
            ),
            outputConfig = TomlOutputConfig()
        ).decodeFromFile(
            deserializer = serializer(),
            tomlFilePath = path.toString()
        )

        private fun loadFromYaml(path: Path) = Yaml(
            configuration = YamlConfiguration(
                strictMode = false
            )
        ).decodeFromSource(
            deserializer = serializer(),
            source = path.rawSource().asOkioSource()
        )

        fun serializeToYaml(config: Config): String = Yaml(
            configuration = YamlConfiguration(
                strictMode = false
            )
        ).encodeToString(
            serializer = serializer(),
            value = config
        )

        private val EscapeOrDelete get() = KeyboardEvent("Escape")
    }
}
