package de.jonasbroeckmann.nav

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

private val terminal = Terminal()



fun main(args: Array<String>) = Nav().main(args)

class Nav : CliktCommand() {
    private val startingDirectory by argument(
        "directory",
        help = "The directory to start in",
        completionCandidates = CompletionCandidates.Path
    )
        .convert { SystemFileSystem.resolve(Path(it)) }
        .optional()
        .validate {
            val metadata = SystemFileSystem.metadataOrNull(it)
                ?: fail("\"$it\": No such file or directory")
            require(metadata.isDirectory) { "\"$it\": Not a directory" }
        }

    override fun run() {
        val config = Config()
        val animation = MainAnimation(
            terminal = terminal,
            config = config,
            startingDirectory = startingDirectory ?: workingDirectory,
            startingCursorIndex = 0
        )

        while (true) {
            val selection = animation.receiveEvents()

            if (selection == null) break

            val metadata = SystemFileSystem.metadataOrNull(selection)
            if (metadata?.isDirectory == true) {
                broadcastChangeDirectory(selection)
                break
            } else if (metadata?.isRegularFile == true) {
                val exitCode = execute(config.editor, selection.toString())
                if (exitCode != 0) {
                    terminal.danger("Received exit code $exitCode")
                }
            }
        }

        animation.terminate()
    }
}


private val navFile = userHome / ".nav-cd"

fun broadcastChangeDirectory(path: Path) {
    if (SystemFileSystem.exists(navFile)) {
        terminal.danger("$navFile already exists")
        return
    }
    SystemFileSystem.sink(navFile).buffered().use {
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


expect val platformName: String
