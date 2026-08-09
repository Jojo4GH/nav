@file:OptIn(ExperimentalForeignApi::class)

package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.GetEnvironmentVariableW
import platform.windows.SetEnvironmentVariableW
import platform.windows.WCHARVar
import kotlin.collections.buildMap

actual object EnvironmentVariables {
    actual operator fun get(key: String): String? = memScoped {
        val bufferSize = GetEnvironmentVariableW(key, null, 0u)
        if (bufferSize == 0u) return null
        val buffer = allocArray<WCHARVar>(bufferSize.toLong())
        val result = GetEnvironmentVariableW(key, buffer, bufferSize)
        if (result == 0u) return null
        return buffer.toKString()
    }

    actual fun get(): Map<String, String> {
        val environ = platform.posix.environ ?: return emptyMap()
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

    actual operator fun set(key: String, value: String?): Boolean = SetEnvironmentVariableW(key, value) != 0
}
