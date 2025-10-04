package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import platform.posix.EINVAL
import platform.posix.ENOENT
import platform.posix.strerror
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

expect fun stat(path: Path): StatResult

sealed interface StatResult {
    sealed interface Error : StatResult {
        val message: String

        value class NotFound(override val message: String) : Error

        value class InvalidArgument(override val message: String) : Error

        value class Other(override val message: String) : Error
    }

    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun fromErrno(errno: Int = platform.posix.posix_errno()) = when (errno) {
            0 -> null
            ENOENT -> Error.NotFound(strerror(errno)?.toKString() ?: "ENOENT")
            EINVAL -> Error.InvalidArgument(strerror(errno)?.toKString() ?: "EINVAL")
            else -> Error.Other(strerror(errno)?.toKString() ?: "Unknown error ($errno)")
        }
    }
}

val StatResult.error get() = this as? StatResult.Error

@OptIn(ExperimentalTime::class)
data class Stat(
    val deviceId: ULong,
    val serialNumber: ULong,
    val mode: Mode,
    val hardlinkCount: UInt,
    val userId: UInt,
    val groupId: UInt,
    val size: Long,
    val lastAccessTime: Instant,
    val lastModificationTime: Instant,
    val lastStatusChangeTime: Instant
) : StatResult {
    data class Mode(
        val isBlockDevice: Boolean,
        val isCharacterDevice: Boolean,
        val isPipe: Boolean,
        val isRegularFile: Boolean,
        val isDirectory: Boolean,
        val isSymbolicLink: Boolean,
        val isSocket: Boolean,
        val user: Permissions,
        val group: Permissions,
        val others: Permissions
    ) {
        data class Permissions(
            val canRead: Boolean,
            val canWrite: Boolean,
            val canExecute: Boolean
        )
    }
}

internal infix fun UInt.mask(mask: Int): Boolean = (this and mask.toUInt()) == mask.toUInt()

internal infix fun UShort.mask(mask: Int): Boolean = (this.toUInt() and mask.toUInt()) == mask.toUInt()

internal infix fun UInt.bit(i: Int): Boolean = ((this shr i) and 1u) != 0u

internal fun Int.fullBinary(): String = (0..<Int.SIZE_BITS)
    .reversed()
    .map { (this ushr it) and 1 }
    .joinToString("")
