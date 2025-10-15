package de.jonasbroeckmann.nav.command

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.core.theme
import com.github.ajalt.clikt.output.Localization
import com.github.ajalt.clikt.output.MordantHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.nullableFlag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.transform.theme
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Theme
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.Constants.BinaryName
import de.jonasbroeckmann.nav.Constants.IssuesUrl
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.BuildConfig
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.Config.Accessibility
import de.jonasbroeckmann.nav.config.Themes
import de.jonasbroeckmann.nav.framework.context.PartialContext
import de.jonasbroeckmann.nav.framework.context.catchAllFatal
import de.jonasbroeckmann.nav.framework.context.printlnOnDebug
import de.jonasbroeckmann.nav.utils.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.writeString

class NavCommand : CliktCommand(name = BinaryName), PartialContext {
    init {
        context {
            terminal = when (val terminalInterface = customTerminalInterface()) {
                null -> Terminal(theme = DefaultTerminalTheme)
                else -> Terminal(theme = DefaultTerminalTheme, terminalInterface = terminalInterface)
            }
            helpOptionNames = setOf("-?", "--help")
            helpFormatter = { context -> NavHelpFormatter(context) }
            localization = NavLocalization
        }
    }

    override fun help(context: Context): String {
        val stylish = listOf(
            Themes.Retro.file("st"),
            Themes.Retro.directory("yl"),
            Themes.Retro.link("i"),
            Themes.Retro.path("sh"),
        ).joinToString("")
        return "The interactive and $stylish replacement for ls & cd!"
    }

    val configurationOptions by ConfigurationOptions()

    class ConfigurationOptions : OptionGroup(
        name = "Configuration",
        help = "Options to configure the behavior of $BinaryName"
    ) {
        val showHiddenEntries by option("-a", "--all").nullableFlag("-h", "--not-all").help {
            "Choose whether hidden entries are shown or not. ${theme.muted("(Overrides other configuration)")}"
        }

        val configPath by option(
            "--config",
            metavar = "path"
        ).help {
            val configPaths = listOf("$${Config.ENV_VAR_NAME}") + Config.DefaultPaths.map { "\"$it\"" }
            helpLines(
                "Explicitly specify the config file to use.",
                theme.muted("Default: Search for config file in ${configPaths.joinToString(" or ")}")
            )
        }

        val editConfig by option(
            "--edit-config",
            help = "Opens the current config file in the editor."
        ).flag()

        val editor by option(
            "--editor",
            metavar = "command"
        ).convert { it.trim() }.help {
            "Explicitly specify the editor to use. ${theme.muted("(Overrides other configuration)")}"
        }

        val forceAnsiLevel by option(
            "--force-ansi",
            metavar = "level"
        )
            .choice(AnsiLevel.entries.associateBy { it.name })
            .help {
                helpLines(
                    "Forces a specific ANSI level (overrides auto-detection):",
                    AnsiLevel.entries.reversed().joinToString(" • ") { level ->
                        when (level) {
                            AnsiLevel.TRUECOLOR -> "${level.name}: 24-bit colors"
                            AnsiLevel.ANSI256 -> "${level.name}: 8-bit colors"
                            AnsiLevel.ANSI16 -> "${level.name}: 4-bit colors"
                            AnsiLevel.NONE -> "${level.name}: No colors"
                        }
                    }.let { theme.muted(it) }
                )
            }

        val renderMode by option(
            "--render",
            metavar = "mode"
        )
            .choice(
                RenderModeOption.entries.associateBy { it.label },
                ignoreCase = true
            )
            .default(Auto, defaultForHelp = RenderModeOption.Auto.label)
            .help {
                helpLines(
                    "Configures how the $BinaryName is rendered:",
                    *RenderModeOption.entries.map { mode ->
                        when (mode) {
                            Auto -> "• ${mode.label}: Automatically detect the best mode based on terminal capabilities (default)"
                            Simple -> "• ${mode.label}: Use a simple color theme"
                            Accessible -> "• ${mode.label}: Use accessibility decorations"
                            SimpleAccessible -> "• ${mode.label}: Use a simple color theme and accessibility decorations"
                            NoColor -> "• ${mode.label}: Disable colors (forces accessibility decorations)"
                        }.let { theme.muted(it) }
                    }.toTypedArray()
                )
            }

        enum class RenderModeOption(
            val label: String,
            val accessibility: Accessibility,
            val forceNoColor: Boolean = false
        ) {
            Auto("auto", Accessibility()),
            Simple("simple", Accessibility(simpleColors = true)),
            Accessible("accessible", Accessibility(decorations = true)),
            SimpleAccessible("simple-accessible", Accessibility(simpleColors = true, decorations = true)),
            NoColor("no-color", Accessibility(simpleColors = true, decorations = true), forceNoColor = true)
        }

        val shell by option(
            "--shell",
            "--correct-init", // Deprecated
            metavar = "shell",
            help = "Uses this shell for command execution. Also signals that the initialization script is being used correctly."
        ).choice(Shell.available).help {
            helpLines(
                "Uses this shell for command execution. Also signals that the initialization script is being used correctly.",
                theme.muted(shellHelpValues)
            )
        }
    }

    private val initOption by mutuallyExclusiveOptions<InitOption>(
        option(
            "--init",
            metavar = "shell"
        ).choice(Shell.available).convert { InitOption.Init(it) }.help {
            helpLines(
                "Prints the initialization script for the specified shell.",
                theme.muted(shellHelpValues)
            )
        },
        option(
            "--init-help",
            "--init-info", // Deprecated
            help = "Prints information about how to initialize $BinaryName correctly"
        ).flag().convert { if (it) InitOption.Info else null },
        option(
            "--profile-command",
            metavar = "shell"
        ).choice(Shell.available).convert { InitOption.ProfileCommand(it) }.help {
            helpLines(
                "Prints the command that should be added to the profile file for the specified shell.",
                theme.muted(shellHelpValues)
            )
        },
        option(
            "--profile-location",
            metavar = "shell"
        ).choice(Shell.available).convert { InitOption.ProfileLocation(it) }.help {
            helpLines(
                "Prints the typical location of the profile file for the specified shell.",
                theme.muted(shellHelpValues)
            )
        },
        name = "Initialization",
        help = "Options related to $BinaryName initialization"
    ).single()

    private sealed interface InitOption {
        data object Info : InitOption

        data class Init(val shell: Shell) : InitOption

        data class ProfileLocation(val shell: Shell) : InitOption

        data class ProfileCommand(val shell: Shell) : InitOption
    }

    override val debugMode by option(
        "--debug",
        help = "Enables debug mode."
    ).flag()

    private val version by option(
        "--version",
        help = "Print version and exit."
    ).flag()

    val directory by argument(
        directoryArgumentName,
        help = "Start $BinaryName in this directory.",
        completionCandidates = CompletionCandidates.Path
    )
        .convert { Path(it).absolute().cleaned() }
        .optional()
        .validate {
            val metadata = it.metadataOrNull() ?: fail("\"$it\": No such file or directory")
            require(metadata.isDirectory) { "\"$it\": Not a directory" }
        }

    override fun helpEpilog(context: Context) = context.theme.muted(
        "Version: ${BuildConfig.VERSION} • Report issues at: $IssuesUrl"
    )

    override val command get() = this

    override val terminal by lazy {
        val commandTerminal = currentContext.terminal
        val detected = commandTerminal.terminalInfo
        Terminal(
            ansiLevel = configurationOptions.forceAnsiLevel
                ?: AnsiLevel.NONE.takeIf { configurationOptions.renderMode.forceNoColor }
                ?: when (detected.ansiLevel) {
                    NONE -> AnsiLevel.ANSI16 // at least ANSI16 if not forced
                    else -> null
                },
            theme = commandTerminal.theme,
            terminalInterface = commandTerminal.terminalInterface,
        )
    }

    override val startingDirectory get() = directory ?: WorkingDirectory

    override val shell get() = configurationOptions.shell

    override fun run() = catchAllFatal {
        runInternal()
    }

    private fun runInternal() {
        if (version) {
            terminal.println("$BinaryName ${BuildConfig.VERSION}")
            return
        }

        printlnOnDebug { "${terminal.terminalInfo}" }

        initOption?.let { initOption ->
            when (initOption) {
                InitOption.Info -> {
                    terminal.println()
                    Shell.printInitInfo(terminal)
                    return
                }
                is InitOption.Init -> {
                    initOption.shell.printInitScript()
                    return
                }
                is InitOption.ProfileLocation -> {
                    initOption.shell.printProfileLocation()
                    return
                }
                is InitOption.ProfileCommand -> {
                    initOption.shell.printProfileCommand()
                    return
                }
            }
        }

        val config = Config.load()

        printlnOnDebug { "Using config: $config" }
        printlnOnDebug { "Serialized:\n${Config.serializeToYaml(config)}" }

        if (configurationOptions.shell == null && !config.suppressInitCheck) {
            terminal.danger("The installation is not complete and some feature will not work.")
            terminal.info("Use --init-help to get more information.")
        }

        val app = App(config)

        if (configurationOptions.editConfig) {
            doEditConfig(app)
        }

        app.main()
    }

    private fun doEditConfig(app: App): Nothing {
        val configPath = Config.findExplicitPath()
            ?: Config.findDefaultPath(mustExist = false)
            ?: run {
                terminal.danger("Can not use any of ${Config.DefaultPaths} as config file.")
                exitProcess(1)
            }
        if (!configPath.exists()) {
            terminal.info("""Config file does not exist yet. Creating new config file at "$configPath" ...""")
            configPath.parent?.createDirectories(mustCreate = false)
            configPath.rawSink().buffered().use {
                it.writeString("# $BinaryName configuration file\n\n")
            }
        }
        terminal.info("""Opening config file at "$configPath" ...""")
        val exitCode = app.openInEditor(configPath)
        exitProcess(exitCode ?: 1)
    }

    companion object {
        private val directoryArgumentName get() = "directory"

        private fun helpLines(vararg lines: String) = lines.joinToString("\u0085")

        private val shellHelpValues get() = "Supported shells: ${Shell.available.keys.joinToString(", ")}"

        private val DefaultTerminalTheme get() = Theme(from = Default) {
            styles["main"] = Themes.Retro.path
            styles["style1"] = Themes.Retro.directory
            styles["style2"] = Themes.Retro.file
            styles["style3"] = Themes.Retro.link
            styles["success"] = TextColors.rgb("#66c322")
            styles["danger"] = TextColors.rgb("#ff2a00")
            styles["warning"] = TextColors.rgb("#ffcc00")
            styles["info"] = TextColors.rgb("#7ecefc")
            styles["muted"] = TextStyles.dim.style
        }
    }

    private class NavHelpFormatter(context: Context) : MordantHelpFormatter(context) {
        override fun styleUsageTitle(title: String) = theme.style("style1")(title)

        override fun styleSectionTitle(title: String) = theme.style("style1")(title)

        override fun normalizeParameter(name: String) = when (name) {
            localization.optionsMetavar() -> theme.style("style2")(super.normalizeParameter(name))
            directoryArgumentName -> theme.style("main")(super.normalizeParameter(name))
            else -> super.normalizeParameter(name)
        }

        override fun styleOptionName(name: String) = theme.style("style2")(name)

        override fun styleArgumentName(name: String) = name

        override fun styleMetavar(metavar: String) = theme.style("style3")(metavar)
    }

    private object NavLocalization : Localization {
        override fun helpOptionMessage() = "Show this message and exit."
    }
}
