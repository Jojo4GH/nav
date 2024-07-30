package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator



val WorkingDirectory: Path by lazy { Path(".").absolute() }
val UserHome: Path by lazy {
    (getenv("HOME") ?: getenv("USERPROFILE"))
        ?.let { Path(it).absolute() }
        ?: throw IllegalStateException("Could not determine user home directory")
}


fun Path.absolute() = SystemFileSystem.resolve(this)
fun Path.exists() = SystemFileSystem.exists(this)
fun Path.metadataOrNull() = SystemFileSystem.metadataOrNull(this)
val Path.isDirectory get() = metadataOrNull()?.isDirectory == true
val Path.isRegularFile get() = metadataOrNull()?.isRegularFile == true
val Path.size get() = metadataOrNull()?.size ?: -1L
fun Path.children() = SystemFileSystem.list(this)
fun Path.createDirectories(mustCreate: Boolean = false) = SystemFileSystem.createDirectories(this, mustCreate = mustCreate)
fun Path.delete(mustExist: Boolean = true) = SystemFileSystem.delete(this, mustExist = mustExist)
fun Path.sink(append: Boolean = false) = SystemFileSystem.sink(this, append = append)
fun Path.source() = SystemFileSystem.source(this)


operator fun Path.div(child: String) = Path(this, child).cleaned()
operator fun Path.div(child: Path) = this / child.toString()

fun Path.cleaned() = Path("$this"
    .replace(SystemPathSeparator, RealSystemPathSeparator)
    .replace("$RealSystemPathSeparator$RealSystemPathSeparator", "$RealSystemPathSeparator")
)


expect val PathsSeparator: Char

expect val RealSystemPathSeparator: Char

