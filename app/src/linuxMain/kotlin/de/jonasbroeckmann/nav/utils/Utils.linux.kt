package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun getEnvironmentVariable(key: String): String? = platform.posix.getenv(key)?.toKString()

actual fun setEnvironmentVariable(key: String, value: String?): Boolean = if (value == null) {
    platform.posix.unsetenv(key) == 0
} else {
    platform.posix.setenv(key, value, 1) == 0
}
