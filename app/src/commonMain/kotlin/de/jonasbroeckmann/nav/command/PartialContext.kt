package de.jonasbroeckmann.nav.command

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.io.files.Path

interface PartialContext : Logger {
    override val terminal: Terminal
    val command: NavCommand
    override val debugMode: Boolean
    val startingDirectory: Path
    val shell: Shell?
}
