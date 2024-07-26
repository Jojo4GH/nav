package de.jonasbroeckmann.nav

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import platform.posix.chdir
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(key: String): String? {
    return platform.posix.getenv(key)?.toKString()
}

@OptIn(ExperimentalNativeApi::class)
actual val RealSystemPathSeparator: Char = when (Platform.osFamily) {
    OsFamily.WINDOWS -> '\\'
    else -> '/'
}