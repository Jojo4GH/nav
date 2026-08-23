package de.jonasbroeckmann.nav.utils

import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import de.jonasbroeckmann.nav.framework.utils.div
import de.jonasbroeckmann.nav.framework.utils.exists
import kotlinx.io.files.Path

fun which(command: String): Path? {
    val path = EnvironmentVariables["PATH"] ?: return null
    val extensions = EnvironmentVariables["PATHEXT"]?.lowercase()?.split(";") ?: emptyList()
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

expect fun exitProcess(status: Int): Nothing

fun String.parseColor() = rgb(this)
