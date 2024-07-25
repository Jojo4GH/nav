package de.jonasbroeckmann.nav

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalNativeApi


@OptIn(ExperimentalForeignApi::class)
actual fun getenv(key: String): String? {
    return platform.posix.getenv(key)?.toKString()
}

actual fun changeDirectory(path: Path): Boolean {
    return platform.posix.chdir(path.toString()) == 0
}


@OptIn(ExperimentalNativeApi::class)
actual val platformName: String = "${Platform.osFamily} on ${Platform.cpuArchitecture}"
