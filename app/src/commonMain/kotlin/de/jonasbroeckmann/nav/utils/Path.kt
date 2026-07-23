package de.jonasbroeckmann.nav.utils

import de.jonasbroeckmann.nav.Constants
import de.jonasbroeckmann.nav.framework.utils.absolute
import de.jonasbroeckmann.nav.framework.utils.createDirectories
import de.jonasbroeckmann.nav.framework.utils.deleteRecursively
import de.jonasbroeckmann.nav.framework.utils.div
import de.jonasbroeckmann.nav.framework.utils.exists
import kotlinx.io.files.Path
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

val WorkingDirectory: Path by lazy { Path(".").absolute() }

val UserHome: Path by lazy {
    (getEnvironmentVariable("HOME") ?: getEnvironmentVariable("USERPROFILE"))
        ?.let { Path(it).absolute() }
        ?: throw IllegalStateException("Could not determine user home directory")
}

inline fun <R> withTemporaryDirectory(label: String = Constants.BinaryName, block: (Path) -> R): R {
    var path: Path
    do {
        path = SystemTemporaryDirectory / "$label-${Random.nextLong().toHexString()}"
    } while (path.exists())
    path.createDirectories()
    try {
        return block(path)
    } finally {
        path.deleteRecursively(mustExist = false)
    }
}

fun Path.cleaned() = Path(
    "$this"
        .replace(SystemPathSeparator, RealSystemPathSeparator)
        .replace("$RealSystemPathSeparator$RealSystemPathSeparator", "$RealSystemPathSeparator")
)

expect val PathsSeparator: Char

expect val RealSystemPathSeparator: Char
