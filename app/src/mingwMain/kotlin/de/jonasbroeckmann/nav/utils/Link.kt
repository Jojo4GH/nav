package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
fun Path.finalPath(): FinalPathResult = useHandle(
    onError = { code -> FinalPathResult.Error(code) },
) { handle ->
    // First call to get the required buffer size
    val bufferSize = GetFinalPathNameByHandleW(
        hFile = handle,
        lpszFilePath = allocArray<TCHARVar>(1),
        cchFilePath = 1u,
        dwFlags = (FILE_NAME_NORMALIZED or VOLUME_NAME_DOS).toUInt()
    )
    if (bufferSize == 0u) {
        return FinalPathResult.Error(GetLastError())
    }
    // Second call to get the actual path
    val buffer = allocArray<TCHARVar>(bufferSize.toLong())
    val resultSize = GetFinalPathNameByHandleW(
        hFile = handle,
        lpszFilePath = buffer,
        cchFilePath = bufferSize,
        dwFlags = (FILE_NAME_NORMALIZED or VOLUME_NAME_DOS).toUInt()
    )
    buffer[resultSize.toLong()] = 0u
    if (resultSize == 0u) {
        return FinalPathResult.Error(GetLastError())
    }
    return FinalPathResult.Success(Path(buffer.toKString().removePrefix("""\\?\""")))
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun <R> Path.useHandle(
    onError: MemScope.(UInt) -> R,
    block: MemScope.(HANDLE) -> R
): R = memScoped {
    val handle = CreateFileW(
        lpFileName = this@useHandle.toString(),
        dwDesiredAccess = GENERIC_READ,
        dwShareMode = FILE_SHARE_WRITE.toUInt(),
        lpSecurityAttributes = null,
        dwCreationDisposition = OPEN_EXISTING.toUInt(),
        dwFlagsAndAttributes = 0u,
        hTemplateFile = null
    )
    if (handle == INVALID_HANDLE_VALUE || handle == null) {
        return onError(GetLastError())
    }
    try {
        block(handle)
    } finally {
        if (CloseHandle(handle) == 0) {
            @Suppress("detekt:ThrowingExceptionFromFinally")
            throw IllegalStateException(
                "CloseHandle for \"${this@useHandle}\" failed: ${getMessageForErrorCode(GetLastError())}"
            )
        }
    }
}

sealed interface FinalPathResult {
    data class Success(val path: Path) : FinalPathResult

    data class Error(val code: UInt) : FinalPathResult {
        val message by lazy { getMessageForErrorCode(code) ?: "Error $code" }
    }
}

val FinalPathResult.success get() = this as? FinalPathResult.Success
val FinalPathResult.error get() = this as? FinalPathResult.Error
