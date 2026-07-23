package de.jonasbroeckmann.nav.framework.utils

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** @see kotlinx.io.files.FileSystem.resolve */
fun Path.absolute() = SystemFileSystem.resolve(this)

/** @see kotlinx.io.files.FileSystem.exists */
fun Path.exists() = SystemFileSystem.exists(this)

/** @see kotlinx.io.files.FileSystem.metadataOrNull */
fun Path.metadataOrNull() = SystemFileSystem.metadataOrNull(this)

/** @see kotlinx.io.files.FileSystem.isDirectory */
fun Path.isDirectory() = SystemFileSystem.isDirectory(this)

/** @see kotlinx.io.files.FileSystem.isRegularFile */
fun Path.isRegularFile() = SystemFileSystem.isRegularFile(this)

/** @see kotlinx.io.files.FileSystem.size */
fun Path.size() = SystemFileSystem.size(this)

/** @see kotlinx.io.files.FileSystem.list */
fun Path.children() = SystemFileSystem.list(this)

/** @see kotlinx.io.files.FileSystem.createDirectories */
fun Path.createDirectories(mustCreate: Boolean = false) = SystemFileSystem.createDirectories(this, mustCreate = mustCreate)

/** @see kotlinx.io.files.FileSystem.atomicMove */
fun Path.atomicMove(to: Path) = SystemFileSystem.atomicMove(this, to)

/** @see kotlinx.io.files.FileSystem.delete */
fun Path.delete(mustExist: Boolean = true) = SystemFileSystem.delete(this, mustExist = mustExist)

/** @see kotlinx.io.files.FileSystem.deleteRecursively */
fun Path.deleteRecursively(mustExist: Boolean = true) = SystemFileSystem.deleteRecursively(this, mustExist = mustExist)

/** @see kotlinx.io.files.FileSystem.copyRecursively */
fun Path.copyRecursively(to: Path) = SystemFileSystem.copyRecursively(this, to)

/** @see kotlinx.io.files.FileSystem.sink */
fun Path.sink(append: Boolean = false) = SystemFileSystem.sink(this, append = append)

/** @see kotlinx.io.files.FileSystem.source */
fun Path.source() = SystemFileSystem.source(this)

val Path.nameAndExtension: Pair<String, String?> get() = Regex("""(.+)\.([^.]+)""").matchEntire(name)?.destructured
    ?.let { (name, extension) -> name to extension }
    ?: (name to null)

operator fun Path.div(child: String) = Path(this, child)

operator fun Path.div(child: Path) = this / child.toString()
