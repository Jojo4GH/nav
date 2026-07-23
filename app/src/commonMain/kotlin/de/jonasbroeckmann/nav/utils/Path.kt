package de.jonasbroeckmann.nav.utils

import de.jonasbroeckmann.nav.framework.utils.absolute
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator

object Paths {
    val WorkingDirectory: Path by lazy { Path(".").absolute() }

    val UserHome: Path by lazy {
        (getEnvironmentVariable("HOME") ?: getEnvironmentVariable("USERPROFILE"))
            ?.let { Path(it).absolute() }
            ?: throw IllegalStateException("Could not determine user home directory")
    }
}

fun Path.cleaned() = Path(
    "$this"
        .replace(SystemPathSeparator, RealSystemPathSeparator)
        .replace("$RealSystemPathSeparator$RealSystemPathSeparator", "$RealSystemPathSeparator")
)

expect val PathsSeparator: Char

expect val RealSystemPathSeparator: Char
