package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.EINVAL
import platform.posix.ENOENT
import platform.posix.strerror

@OptIn(ExperimentalForeignApi::class)
fun StatResult.Companion.fromErrno(errno: Int = platform.posix.posix_errno()) = when (errno) {
    0 -> null
    ENOENT -> StatResult.Error.NotFound(strerror(errno)?.toKString() ?: "ENOENT")
    EINVAL -> StatResult.Error.InvalidArgument(strerror(errno)?.toKString() ?: "EINVAL")
    else -> StatResult.Error.Other(strerror(errno)?.toKString() ?: "Unknown error ($errno)")
}
