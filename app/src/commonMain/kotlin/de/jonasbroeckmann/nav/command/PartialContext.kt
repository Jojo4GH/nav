package de.jonasbroeckmann.nav.command

import kotlinx.io.files.Path

interface PartialContext : TerminalLogger {
    val command: NavCommand
    val startingDirectory: Path
    val shell: Shell?
}
