package de.jonasbroeckmann.nav.command

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.Constants.IssuesUrl
import de.jonasbroeckmann.nav.utils.exitProcess
import kotlinx.io.files.Path

interface PartialContext {
    val terminal: Terminal
    val command: NavCommand
    val debugMode: Boolean
    val startingDirectory: Path
    val shell: Shell?
}

fun PartialContext.dangerThrowable(e: Throwable, message: Any?, includeStackTrace: Boolean = debugMode) {
    terminal.danger(message)
    if (includeStackTrace) terminal.danger(e.stackTraceToString())
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

inline fun <R> PartialContext.catchAllFatal(
    cleanupOnError: (Throwable) -> Unit = { },
    block: () -> R
): R = try {
    block()
} catch (e: Throwable) {
    cleanupOnError(e)
    dangerThrowable(e, "An unexpected error occurred: ${e.message}", includeStackTrace = true)
    terminal.info("Please report this issue at: $IssuesUrl")
    exitProcess(1)
}

inline fun PartialContext.catchAllDebug(
    block: () -> Unit
) = try {
    block()
} catch (e: Throwable) {
    if (debugMode) dangerThrowable(e, "An unexpected error occurred: ${e.message}", includeStackTrace = true)
    infoOnDebug { "Please report this issue at: $IssuesUrl" }
}
