package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.arguments.validate
import com.github.ajalt.mordant.terminal.Terminal
import com.kgit2.kommand.process.Command
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.writeString




fun main(args: Array<String>) = Nav().main(args)

class Nav : CliktCommand() {
    private val startingDirectory by argument(
        "directory",
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

    override fun run() {
        val terminal = Terminal()
//        val config = Config.load(terminal)
        val config = Config()
        val animation = MainAnimation(
            terminal = terminal,
            config = config,
            startingDirectory = startingDirectory ?: WorkingDirectory,
            startingCursorIndex = 0
        )

        while (true) {
            val selection = animation.main()

            if (selection == null) break

            if (selection.isDirectory) {
                broadcastChangeDirectory(selection)
                break
            } else if (selection.isRegularFile) {
                val exitCode = Command(config.editor)
                    .args(selection.toString())
                    .spawn()
                    .wait()
                if (exitCode != 0) {
                    terminal.danger("Received exit code $exitCode from ${config.editor}")
                }
            }
        }

        animation.terminate()
    }
}


private val navFile = UserHome / ".nav-cd"

fun broadcastChangeDirectory(path: Path) {
    if (navFile.exists()) {
//        terminal.danger("$navFile already exists")
//        return
        navFile.delete()
    }
    navFile.sink().buffered().use {
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
