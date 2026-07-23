package de.jonasbroeckmann.nav.framework.utils

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** @see kotlinx.io.files.FileSystem.resolve */
fun Path.absolute() = SystemFileSystem.resolve(this)

fun Path.exists() = SystemFileSystem.exists(this)

fun Path.metadataOrNull() = SystemFileSystem.metadataOrNull(this)

fun Path.isDirectory() = SystemFileSystem.isDirectory(this)

fun Path.isRegularFile() = SystemFileSystem.isRegularFile(this)

fun Path.size() = metadataOrNull()?.size ?: -1L

fun Path.children() = SystemFileSystem.list(this)

/** @see kotlinx.io.files.FileSystem.createDirectories */
fun Path.createDirectories(mustCreate: Boolean = false) = SystemFileSystem.createDirectories(this, mustCreate = mustCreate)

/** @see kotlinx.io.files.FileSystem.atomicMove */
fun Path.atomicMove(to: Path) = SystemFileSystem.atomicMove(this, to)

fun Path.delete(mustExist: Boolean = true) = SystemFileSystem.delete(this, mustExist = mustExist)

fun Path.deleteRecursively(mustExist: Boolean = true) = SystemFileSystem.deleteRecursively(this, mustExist = mustExist)

fun Path.copyRecursively(to: Path) = SystemFileSystem.copyRecursively(this, to)
fun Path.rawSink(append: Boolean = false) = SystemFileSystem.sink(this, append = append)

fun Path.rawSource() = SystemFileSystem.source(this)

val Path.nameAndExtension: Pair<String, String?> get() = Regex("""(.+)\.([^.]+)""").matchEntire(name)?.destructured
    ?.let { (name, extension) -> name to extension }
    ?: (name to null)

operator fun Path.div(child: String) = Path(this, child)

operator fun Path.div(child: Path) = this / child.toString()
