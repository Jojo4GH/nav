package de.jonasbroeckmann.nav.command

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.terminal.success
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.Logger

interface TerminalLogger : Logger {
    val terminal: Terminal

    override fun println(message: Any?) = terminal.println(message)
    override fun info(message: Any?) = terminal.info(message)
    override fun success(message: Any?) = terminal.success(message)
    override fun warning(message: Any?) = terminal.warning(message)
    override fun danger(message: Any?) = terminal.danger(message)
}