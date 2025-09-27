package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import platform.posix.PATH_MAX
import platform.posix.readlink

@OptIn(ExperimentalForeignApi::class)
actual fun readLink(path: Path): String? {
    memScoped {
        val result: CPointer<ByteVar> = allocArray(PATH_MAX)
        readlink(path.toString(), result, PATH_MAX.toULong())
        return result.toKString()
    }
}
