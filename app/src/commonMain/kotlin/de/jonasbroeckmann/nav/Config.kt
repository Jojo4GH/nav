@file:UseSerializers(KeyboardEventAsStringSerializer::class)
package de.jonasbroeckmann.nav

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.file.TomlFileReader
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.EntryColumn
import de.jonasbroeckmann.nav.app.State
import de.jonasbroeckmann.nav.utils.*
import kotlinx.io.files.Path
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

    val colors: Colors = Colors.Retro,
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
    data class Colors(
        val path: String = Retro.path,
        val filter: String = Retro.filter,
        val filterMarker: String = Retro.filterMarker,
        val keyHints: String = Retro.keyHints,

        val permissionRead: String = Retro.permissionRead,
        val permissionWrite: String = Retro.permissionWrite,
        val permissionExecute: String = Retro.permissionExecute,
        val hardlinkCount: String = Retro.hardlinkCount,
        val user: String = Retro.user,
        val group: String = Retro.group,
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
                hardlinkCount = "13A10E",
                user = "C50F1F",
                group = "C50F1F",
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
            currentEntry: State.Entry
        ) = description.replacePlaceholders(
            state = state,
            currentEntry = currentEntry
        )

        context(state: State)
        fun computeCommand(
            currentEntry: State.Entry
        ) = command.replacePlaceholders(
            state = state,
            currentEntry = currentEntry
        )

        private fun String.replacePlaceholders(
            state: State,
            currentEntry: State.Entry
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

        context(context: RunContext)
        fun findExplicitPath(): Path? {
            return context.command.configurationOptions.configPath?.let { Path(it) }
                ?: getenv(ENV_VAR_NAME)?.takeUnless { it.isBlank() }?.let { Path(it) }
        }

        context(context: RunContext)
        fun load() = loadInternal()
            .let {
                // override editor from command line argument
                val editorFromCLI = context.command.configurationOptions.editor
                if (editorFromCLI != null) {
                    context.printlnOnDebug { "Using editor from command line argument: $editorFromCLI" }
                    return it.copy(editor = editorFromCLI)
                }
                // fill in default editor
                if (it.editor == null) {
                    it.copy(editor = findDefaultEditor())
                } else it
            }

        context(context: RunContext)
        private fun loadInternal(): Config {
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

        context(context: RunContext)
        private fun findDefaultEditor(): String? {
            context.printlnOnDebug { "Searching for default editor:" }

            fun checkEnvVar(name: String): String? {
                val value = getenv(name)?.trim() ?: run {
                    context.printlnOnDebug { $$"  $$$name not set" }
                    return null
                }
                if (value.isBlank()) {
                    context.printlnOnDebug { $$"  $$$name is empty" }
                    return null
                }
                    context.printlnOnDebug { $$"  Using value of $$$name: $$value" }
                return value
            }

            fun checkProgram(name: String): String? {
                val path = which(name) ?: run {
                    context.printlnOnDebug { $$"  $$name not found in $PATH" }
                    return null
                }
                    context.printlnOnDebug { "  Found $name at $path" }
                return "\"$path\"" // quote path to handle spaces
            }

            return sequence {
                yield(checkEnvVar("EDITOR"))
                yield(checkEnvVar("VISUAL"))
                yield(checkProgram("nano"))
                yield(checkProgram("nvim"))
                yield(checkProgram("vim"))
                yield(checkProgram("vi"))
                yield(checkProgram("code"))
                yield(checkProgram("notepad"))
            }.filterNotNull().firstOrNull().also {
                if (it == null) {
                    context.terminal.danger("Could not find a default editor")
                    context.terminal.warning(specifyEditorMessage)
                }
            }
        }

        val specifyEditorMessage: String get() {
            return $$"""Please specify an editor via the --editor CLI option, the editor config option or the $EDITOR environment variable"""
        }

        private val EscapeOrDelete get() = KeyboardEvent("Escape")
    }
}

interface ConfigProvider {
    val config: Config
}
