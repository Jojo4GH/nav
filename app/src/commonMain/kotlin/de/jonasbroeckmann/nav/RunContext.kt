package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import kotlinx.io.files.Path

interface RunContext {
    val terminal: Terminal
    val command: NavCommand
    val debugMode: Boolean get() = command.debugMode
    val startingDirectory: Path get() = command.startingDirectory ?: WorkingDirectory
    val shell: Shell? get() = command.configurationOptions.shell

    companion object {

        operator fun invoke(
            terminal: Terminal,
            command: NavCommand
        ): RunContext = Impl(terminal, command)

        private data class Impl(
            override val terminal: Terminal,
            override val command: NavCommand
        ) : RunContext
    }
}

fun RunContext.dangerThrowable(e: Throwable, message: Any?) {
    terminal.danger(message)
    dangerOnDebug { e.stackTraceToString() }
}

fun RunContext.printlnOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.println(lazyMessage())
}

fun RunContext.infoOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

fun RunContext.warningOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

fun RunContext.dangerOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.danger(lazyMessage())
}
