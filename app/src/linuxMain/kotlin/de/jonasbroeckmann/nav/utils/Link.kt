package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import platform.posix.PATH_MAX
import platform.posix.errno
import platform.posix.readlink
import platform.posix.strerror

@OptIn(ExperimentalForeignApi::class)
fun readLink(path: Path): ReadLinkResult = memScoped {
    val bufferSize = PATH_MAX + 1
    val buffer = allocArray<ByteVar>(bufferSize)
    val length = readlink(path.toString(), buffer, bufferSize.toULong())
    if (length < 0) {
        return ReadLinkResult.Error(strerror(errno)?.toKString() ?: "Unknown error (${errno})")
    }
    buffer[length] = 0
    val result = Path(buffer.toKString())
    if (result.isAbsolute) {
        ReadLinkResult.Success.Absolute(result)
    } else {
        ReadLinkResult.Success.Relative(path.parent!!, result)
    }
}

sealed interface ReadLinkResult {
    sealed interface Success : ReadLinkResult {
        val value: Path
        val target: Path
        data class Absolute(override val value: Path) : Success {
            override val target get() = value
        }
        data class Relative(val origin: Path, override val value: Path) : Success {
            override val target get() = origin / value
        }
    }

    data class Error(val message: String) : ReadLinkResult
}

val ReadLinkResult.error get() = this as? ReadLinkResult.Error
