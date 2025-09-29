package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.DWORD
import platform.windows.FORMAT_MESSAGE_ALLOCATE_BUFFER
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageW
import platform.windows.LPWSTRVar
import platform.windows.LocalFree

@OptIn(ExperimentalForeignApi::class)
fun getMessageForErrorCode(errorCode: DWORD): String? = memScoped {
    val bufferVar = alloc<LPWSTRVar>()
    val length = FormatMessageW(
        dwFlags = (FORMAT_MESSAGE_ALLOCATE_BUFFER or FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).toUInt(),
        lpSource = null,
        dwMessageId = errorCode,
        dwLanguageId = 0u,
        lpBuffer = bufferVar.ptr.reinterpret(),
        nSize = 0u,
        Arguments = null
    )
    if (length == 0u) {
        return null
    }
    val str = bufferVar.value?.toKString()
    LocalFree(bufferVar.value)
    return str
}

fun getLastErrorMessage() = getMessageForErrorCode(platform.windows.GetLastError())
