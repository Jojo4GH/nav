package de.jonasbroeckmann.nav

import kotlinx.io.files.Path


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
