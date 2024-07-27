package de.jonasbroeckmann.nav

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(key: String): String? {
    return platform.posix.getenv(key)?.toKString()
}
