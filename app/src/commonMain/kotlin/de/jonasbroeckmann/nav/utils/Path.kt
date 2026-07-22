package de.jonasbroeckmann.nav.utils

import de.jonasbroeckmann.nav.Constants
import de.jonasbroeckmann.nav.framework.utils.copyRecursively
import de.jonasbroeckmann.nav.framework.utils.deleteRecursively
import de.jonasbroeckmann.nav.framework.utils.isDirectory
import de.jonasbroeckmann.nav.framework.utils.isRegularFile
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random

val WorkingDirectory: Path by lazy { Path(".").absolute() }

val UserHome: Path by lazy {
    (getEnvironmentVariable("HOME") ?: getEnvironmentVariable("USERPROFILE"))
        ?.let { Path(it).absolute() }
        ?: throw IllegalStateException("Could not determine user home directory")
}

/** @see FileSystem.resolve */
fun Path.absolute() = SystemFileSystem.resolve(this)

fun Path.exists() = SystemFileSystem.exists(this)

fun Path.metadataOrNull() = SystemFileSystem.metadataOrNull(this)

fun Path.isDirectory() = SystemFileSystem.isDirectory(this)

fun Path.isRegularFile() = SystemFileSystem.isRegularFile(this)

fun Path.size() = metadataOrNull()?.size ?: -1L

fun Path.children() = SystemFileSystem.list(this)

/** @see FileSystem.createDirectories */
fun Path.createDirectories(mustCreate: Boolean = false) = SystemFileSystem.createDirectories(this, mustCreate = mustCreate)

/** @see FileSystem.atomicMove */
fun Path.atomicMove(to: Path) = SystemFileSystem.atomicMove(this, to)

fun Path.delete(mustExist: Boolean = true) = SystemFileSystem.delete(this, mustExist = mustExist)

fun Path.deleteRecursively(mustExist: Boolean = true) = SystemFileSystem.deleteRecursively(this, mustExist = mustExist)

fun Path.copyRecursively(to: Path) = SystemFileSystem.copyRecursively(this, to)

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

fun Path.rawSink(append: Boolean = false) = SystemFileSystem.sink(this, append = append)

fun Path.rawSource() = SystemFileSystem.source(this)

val Path.nameAndExtension: Pair<String, String?> get() = Regex("""(.+)\.([^.]+)""").matchEntire(name)?.destructured
    ?.let { (name, extension) -> name to extension }
    ?: (name to null)

operator fun Path.div(child: String) = Path(this, child) //.cleaned()

operator fun Path.div(child: Path) = this / child.toString()

fun Path.cleaned() = Path(
    "$this"
        .replace(SystemPathSeparator, RealSystemPathSeparator)
        .replace("$RealSystemPathSeparator$RealSystemPathSeparator", "$RealSystemPathSeparator")
)

expect val PathsSeparator: Char

expect val RealSystemPathSeparator: Char
