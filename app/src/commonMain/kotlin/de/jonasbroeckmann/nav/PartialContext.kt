package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import kotlinx.io.files.Path

interface PartialContext {
    val terminal: Terminal
    val command: NavCommand
    val debugMode: Boolean
    val startingDirectory: Path
    val shell: Shell?
}

fun PartialContext.dangerThrowable(e: Throwable, message: Any?) {
    terminal.danger(message)
    dangerOnDebug { e.stackTraceToString() }
}

fun PartialContext.printlnOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.println(lazyMessage())
}

fun PartialContext.infoOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

fun PartialContext.warningOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

fun PartialContext.dangerOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.danger(lazyMessage())
}
