package de.jonasbroeckmann.nav

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi


@OptIn(ExperimentalForeignApi::class)
actual fun getenv(key: String): String? {
    return platform.posix.getenv(key)?.toKString()
}

actual fun changeDirectory(path: Path): Boolean {
    return chdir(path.toString()) == 0
}


@OptIn(ExperimentalNativeApi::class)
actual val platformName: String = "${Platform.osFamily} on ${Platform.cpuArchitecture}"


@OptIn(ExperimentalNativeApi::class)
actual val RealSystemPathSeparator: Char = when (Platform.osFamily) {
    OsFamily.WINDOWS -> '\\'
    else -> '/'
}



