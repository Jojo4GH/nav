package de.jonasbroeckmann.nav.command

import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.Constants
import de.jonasbroeckmann.nav.utils.exitProcess

interface Logger {
    val terminal: Terminal
    val debugMode: Boolean
}

fun Logger.dangerThrowable(e: Throwable, message: Any?, includeStackTrace: Boolean = debugMode) {
    terminal.danger(message)
    if (includeStackTrace) terminal.danger(e.stackTraceToString())
}

inline fun Logger.printlnOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.println(lazyMessage())
}

inline fun Logger.infoOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

inline fun Logger.warningOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.info(lazyMessage())
}

inline fun Logger.dangerOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) terminal.danger(lazyMessage())
}

inline fun <R> Logger.catchAllFatal(
    cleanupOnError: (Throwable) -> Unit = { },
    block: () -> R
): R = try {
    block()
} catch (e: Throwable) {
    cleanupOnError(e)
    dangerThrowable(e, "An unexpected error occurred: ${e.message}", includeStackTrace = true)
    terminal.info("Please report this issue at: ${Constants.IssuesUrl}")
    exitProcess(1)
}

inline fun Logger.catchAllDebug(
    block: () -> Unit
) = try {
    block()
} catch (e: Throwable) {
    if (debugMode) dangerThrowable(e, "An unexpected error occurred: ${e.message}", includeStackTrace = true)
    infoOnDebug { "Please report this issue at: ${Constants.IssuesUrl}" }
}
