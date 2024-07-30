package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import de.jonasbroeckmann.nav.app.BuildConfig
import kotlinx.io.files.Path


fun main(args: Array<String>) = Nav().main(args)

val NavFileInUserHome = Path(".nav-cd")
val NavFile = UserHome / NavFileInUserHome

class Nav : CliktCommand() {
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
        if (version) {
            println("nav ${BuildConfig.VERSION}")
            return
        }

        init?.let {
            val shell = Shells.entries.firstOrNull { shell -> shell.shell.equals(it, ignoreCase = true) }
                ?: error("Unknown shell: $init")
            shell.printInitScript()
            return
        }

        App(
            startingDirectory = startingDirectory ?: WorkingDirectory,
            debugMode = debugMode
        ).main()
    }
}


