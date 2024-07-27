package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString

private val terminal = Terminal()



fun main() {
    val config = Config()
    val animation = MainAnimation(
        terminal = terminal,
        config = config,
        startingDirectory = workingDirectory,
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
