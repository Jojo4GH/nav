package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.exitProcess

interface Logger {
    val debugMode: Boolean

    fun println(message: Any?)

    fun info(message: Any?)

    fun success(message: Any?)

    fun warning(message: Any?)

    fun danger(message: Any?)
}

fun Logger.dangerThrowable(e: Throwable, message: Any?, includeStackTrace: Boolean = debugMode) {
    danger(message)
    if (includeStackTrace) danger(e.stackTraceToString())
}

inline fun Logger.printlnOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) println(lazyMessage())
}

inline fun Logger.infoOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) info(lazyMessage())
}

inline fun Logger.warningOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) info(lazyMessage())
}

inline fun Logger.dangerOnDebug(lazyMessage: () -> Any?) {
    if (debugMode) danger(lazyMessage())
}

inline fun <R> Logger.catchAllFatal(
    cleanupOnError: (Throwable) -> Unit = { },
    block: () -> R
): R = try {
    block()
} catch (e: Throwable) {
    cleanupOnError(e)
    dangerThrowable(e, "An unexpected error occurred: ${e.message}", includeStackTrace = true)
    info("Please report this issue at: ${Constants.IssuesUrl}")
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
