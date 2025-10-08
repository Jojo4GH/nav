package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.GetEnvironmentVariableW
import platform.windows.SetEnvironmentVariableW
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
actual fun getEnvironmentVariable(key: String): String? = memScoped {
    val bufferSize = GetEnvironmentVariableW(key, null, 0u)
    if (bufferSize == 0u) return null
    val buffer = allocArray<WCHARVar>(bufferSize.toLong())
    val result = GetEnvironmentVariableW(key, buffer, bufferSize)
    if (result == 0u) return null
    return buffer.toKString()
}

actual fun setEnvironmentVariable(key: String, value: String?): Boolean = SetEnvironmentVariableW(key, value) != 0
