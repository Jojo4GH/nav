package de.jonasbroeckmann.nav.framework.utils

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

/**
 * @see FileSystem.metadataOrNull
 * @see kotlinx.io.files.FileMetadata.isDirectory
 */
fun FileSystem.isDirectory(path: Path) = metadataOrNull(path)?.isDirectory == true

/**
 * @see FileSystem.metadataOrNull
 * @see kotlinx.io.files.FileMetadata.isRegularFile
 */
fun FileSystem.isRegularFile(path: Path) = metadataOrNull(path)?.isRegularFile == true

/**
 * @see FileSystem.metadataOrNull
 * @see kotlinx.io.files.FileMetadata.size
 */
fun FileSystem.size(path: Path) = metadataOrNull(path)?.size ?: -1L

fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = true) {
    when {
        isDirectory(path) -> {
            list(path).forEach { deleteRecursively(it, mustExist = mustExist) }
            delete(path, mustExist = mustExist)
        }
        else -> delete(path, mustExist = mustExist)
    }
}

fun FileSystem.copyRecursively(from: Path, to: Path) {
    if (isDirectory(from)) {
        createDirectories(to)
        list(from).forEach { copyRecursively(it, Path(to, it.name)) }
    } else {
        source(from).buffered().use { source ->
            sink(to).buffered().use { sink ->
                source.transferTo(sink)
            }
        }
    }
}
