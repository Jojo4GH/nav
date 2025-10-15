package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path

expect fun getEnvironmentVariable(key: String): String?

expect fun setEnvironmentVariable(key: String, value: String?): Boolean

fun which(command: String): Path? {
    val path = getEnvironmentVariable("PATH") ?: return null
    val extensions = getEnvironmentVariable("PATHEXT")?.lowercase()?.split(";") ?: emptyList()
    return path
        .splitToSequence(PathsSeparator)
        .flatMap {
            sequence {
                yield(Path(it) / command)
                yieldAll(extensions.asSequence().map { ext -> Path(it) / "$command$ext" })
            }
        }
        .firstOrNull { it.exists() }
}

infix fun Boolean.implies(other: Boolean) = !this || other

expect fun exitProcess(status: Int): Nothing
