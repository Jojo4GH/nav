package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.InputReceiver
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import com.kgit2.kommand.process.Command
import de.jonasbroeckmann.nav.app.BuildConfig
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.writeString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


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

        val terminal = Terminal()
        val config = Config.load(terminal)
        val animation = MainAnimation(
            terminal = terminal,
            config = config,
            startingDirectory = startingDirectory ?: WorkingDirectory,
            startingCursorIndex = 0,
            debugMode = debugMode
        )

        while (true) {
            animation.update() // update once to show initial state
            val selection = terminal.handleRawInput(
                sequenceTimout = config.inputTimeoutMillis.let {
                    if (config.inputTimeoutMillis > 0) it.milliseconds else Duration.INFINITE
                }
            ) { event ->
                animation.update(event)
            }

            if (selection == null) break

            if (selection.isDirectory) {
                broadcastChangeDirectory(selection)
                break
            } else if (selection.isRegularFile) {
                animation.clear() // hide animation before opening editor
                // open editor
                val exitCode = Command(config.editor)
                    .args(selection.toString())
                    .spawn()
                    .wait()
                if (exitCode != 0) {
                    terminal.danger("Received exit code $exitCode from ${config.editor}")
                }
            }
        }

        if (!config.clearOnExit) {
            // draw one last frame
            animation.update()
            animation.stop()
        } else {
            animation.clear()
        }
    }
}


fun <R> Terminal.handleRawInput(sequenceTimout: Duration, handler: (InputEvent) -> InputReceiver.Status<R>): R {
    enterRawMode().use { rawMode ->
        while (true) {
            val event = try {
                rawMode.readEvent(sequenceTimout)
            } catch (e: RuntimeException) {
                continue // on timeout try again
            }
            when (val status = handler(event)) {
                is InputReceiver.Status.Continue -> continue
                is InputReceiver.Status.Finished -> return status.result
            }
        }
    }
}


fun broadcastChangeDirectory(path: Path) {
    if (NavFile.exists()) {
//        terminal.danger("$NavFile already exists")
//        return
        NavFile.delete()
    }
    NavFile.sink().buffered().use {
        it.writeString(path.toString())
    }
}


data class Entry(
    val path: Path,
    val stat: Stat
) {
    val isDirectory get() = stat.mode.isDirectory
    val isRegularFile get() = stat.mode.isRegularFile
    val isSymbolicLink get() = stat.mode.isSymbolicLink
    val size get() = stat.size.takeIf { it >= 0 && !isDirectory }
}
