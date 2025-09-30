package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
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
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.Config.Accessibility
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.BuildConfig
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.exitProcess
import de.jonasbroeckmann.nav.utils.metadataOrNull
import kotlinx.io.files.Path

class NavCommand : CliktCommand(name = BinaryName), PartialContext {
    val directory by argument(
        "DIRECTORY",
        help = "The directory to start in",
        completionCandidates = CompletionCandidates.Path
    )
        .convert { Path(it).absolute().cleaned() }
        .optional()
        .validate {
            val metadata = it.metadataOrNull() ?: fail("\"$it\": No such file or directory")
            require(metadata.isDirectory) { "\"$it\": Not a directory" }
        }

    val configurationOptions by ConfigurationOptions()

    class ConfigurationOptions : OptionGroup(
        name = "Configuration",
        help = "Options to configure the behavior of $BinaryName"
    ) {
        val configPath by option(
            "--config",
            metavar = "PATH",
            help = "Explicitly specify the config file to use " +
                "(default: looks for config file at $${Config.ENV_VAR_NAME} or \"${Config.DefaultPath}\")"
        )

        val shell by option(
            "--shell",
            "--correct-init", // Deprecated
            metavar = "SHELL",
            help = "Uses this shell for command execution. Also signals that the initialization script is being used correctly"
        ).choice(Shell.available)

        val editor by option(
            "--editor",
            metavar = "COMMAND",
            help = "Explicitly specify the editor to use (overrides all configuration)"
        ).convert { it.trim() }

        val editConfig by option(
            "--edit-config",
            help = "Opens the current config file in the editor"
        ).flag()

        val renderMode by option(
            "--render",
            metavar = "MODE",
            help = "Configures how the UI is rendered"
        )
            .choice(
                RenderModeOption.entries.associateBy { it.label },
                ignoreCase = true
            )
            .default(Auto, defaultForHelp = RenderModeOption.Auto.label)

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

        val forceAnsiLevel by option(
            "--force-ansi",
            metavar = "LEVEL",
            help = "Forces a specific ANSI level (overrides auto-detection)"
        ).choice(AnsiLevel.entries.associateBy { it.name })
    }

    private val initOption by mutuallyExclusiveOptions<InitOption>(
        option(
            "--init-help",
            "--init-info", // Deprecated
            help = "Prints information about how to initialize $BinaryName correctly"
        ).flag().convert { if (it) InitOption.Info else null },
        option(
            "--init",
            metavar = "SHELL",
            help = "Prints the initialization script for the specified shell"
        ).choice(Shell.available).convert { InitOption.Init(it) },
        option(
            "--profile-location",
            metavar = "SHELL",
            help = "Prints the typical location of the profile file for the specified shell"
        ).choice(Shell.available).convert { InitOption.ProfileLocation(it) },
        option(
            "--profile-command",
            metavar = "SHELL",
            help = "Prints the command that should be added to the profile file for the specified shell"
        ).choice(Shell.available).convert { InitOption.ProfileCommand(it) },
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
        help = "Enables debug mode"
    ).flag()

    private val version by option(
        "--version",
        help = "Print version and exit"
    ).flag()

    override val command get() = this

    override val terminal by lazy {
        val detected by lazy { Terminal().terminalInfo }
        Terminal(
            ansiLevel = configurationOptions.forceAnsiLevel
                ?: AnsiLevel.NONE.takeIf { configurationOptions.renderMode.forceNoColor }
                ?: when (detected.ansiLevel) {
                    NONE -> AnsiLevel.ANSI16 // at least ANSI16 if not forced
                    else -> null
                }
        )
    }

    override val startingDirectory get() = directory ?: WorkingDirectory

    override val shell get() = configurationOptions.shell

    override fun run() {
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
                    terminal.println()
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

        if (configurationOptions.shell == null && !config.suppressInitCheck) {
            terminal.danger("The installation is not complete and some feature will not work.")
            terminal.info("Use --init-help to get more information.")
        }

        val app = App(config)

        if (configurationOptions.editConfig) {
            val configPath = Config.findExplicitPath() ?: Config.DefaultPath
            terminal.info("""Opening config file at "$configPath" ...""")
            val exitCode = app.openInEditor(configPath)
            exitProcess(exitCode ?: 1)
        }

        app.main()
    }

    companion object {
        val BinaryName get() = BuildConfig.BINARY_NAME
    }
}
