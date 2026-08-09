@file:OptIn(ExperimentalForeignApi::class)

package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString

actual object EnvironmentVariables {
    actual operator fun get(key: String): String? = platform.posix.getenv(key)?.toKString()

    actual fun get(): Map<String, String> {
        val environ = platform.posix.__environ ?: return emptyMap()
        return buildMap {
            var i = 0
            while (true) {
                val pointer = environ[i++] ?: break
                val pair = pointer.toKString().split('=', limit = 2)
                if (pair.size == 2) {
                    put(pair[0], pair[1])
                }
            }
        }
    }
    actual operator fun set(key: String, value: String?): Boolean = when (value) {
        null -> platform.posix.unsetenv(key) == 0
        else -> platform.posix.setenv(key, value, 1) == 0
    }
}
