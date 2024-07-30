package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalNativeApi


expect fun getenv(key: String): String?


fun which(command: String): Path? {
    val path = getenv("PATH") ?: return null
    val extensions = getenv("PATHEXT")?.lowercase()?.split(";") ?: emptyList()
    return path.splitToSequence(PathsSeparator)
        .flatMap {
            sequence {
                yield(Path(it) / command)
                yieldAll(extensions.asSequence().map { ext -> Path(it) / "$command$ext" })
            }
        }
        .firstOrNull { it.exists() }
}


fun Iterable<String>.commonPrefix(): String {
    val iter = iterator()
    if (!iter.hasNext()) return ""
    var prefix = iter.next()
    while (iter.hasNext()) {
        val next = iter.next()
        prefix = prefix.commonPrefixWith(next)
    }
    return prefix
}

@OptIn(ExperimentalNativeApi::class)
val OsFamily.isUnix get() = when (this) {
    OsFamily.UNKNOWN -> false
    OsFamily.MACOSX -> true
    OsFamily.IOS -> true
    OsFamily.LINUX -> true
    OsFamily.WINDOWS -> false
    OsFamily.ANDROID -> false
    OsFamily.WASM -> false
    OsFamily.TVOS -> true
    OsFamily.WATCHOS -> true
}

