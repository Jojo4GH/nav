package de.jonasbroeckmann.nav.command

import kotlinx.io.files.Path

interface PartialContext : TerminalLogger {
    val commandOptions: CommandOptions
    val startingDirectory: Path
}
