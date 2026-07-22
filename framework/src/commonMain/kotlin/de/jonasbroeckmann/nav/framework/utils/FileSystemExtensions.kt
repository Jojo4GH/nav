package de.jonasbroeckmann.nav.framework.utils

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

fun FileSystem.isDirectory(path: Path) = metadataOrNull(path)?.isDirectory == true

fun FileSystem.isRegularFile(path: Path) = metadataOrNull(path)?.isRegularFile == true

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
