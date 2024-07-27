package de.jonasbroeckmann.nav

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator

val workingDirectory: Path = SystemFileSystem.resolve(Path("."))
val userHome: Path = (getenv("HOME") ?: getenv("USERPROFILE"))
    ?.let { SystemFileSystem.resolve(Path(it)) }
    ?: throw IllegalStateException("Could not determine user home directory")



expect fun getenv(key: String): String?

expect val PathsSeparator: Char

expect val RealSystemPathSeparator: Char

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

operator fun Path.div(child: String) = Path(this, child).cleaned()

fun Path.cleaned() = Path("$this".replace(SystemPathSeparator, RealSystemPathSeparator))