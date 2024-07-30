package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.terminal.Terminal
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.BuildConfig
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.metadataOrNull
import kotlinx.io.files.Path


class NavCommand : CliktCommand() {
    private val startingDirectory by argument(
        "DIRECTORY",
        help = "The directory to start in",
        completionCandidates = CompletionCandidates.Path
    )
        .convert { Path(it).absolute().cleaned() }
        .optional()
        .validate {
            val metadata = it.metadataOrNull()
                ?: fail("\"$it\": No such file or directory")
            require(metadata.isDirectory) { "\"$it\": Not a directory" }
        }

    private val init by option(
        "--init",
        metavar = "SHELL",
        help = "Prints the initialization script for the specified shell"
    )

    private val debugMode by option(
        "--debug",
        help = "Enables debug mode"
    ).flag()

    private val version by option(
        "--version",
        help = "Print version"
    ).flag()

    override fun run() {
        val terminal = Terminal()

        if (version) {
            terminal.println("$BinaryName ${BuildConfig.VERSION}")
            return
        }

        init?.let {
            runInit(it)
            return
        }

        val config = Config.load(terminal)

        App(
            terminal = terminal,
            config = config,
            startingDirectory = startingDirectory ?: WorkingDirectory,
            debugMode = debugMode
        ).main()
    }

    companion object {
        val BinaryName get() = BuildConfig.BINARY_NAME
    }
}