package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path
import kotlin.jvm.JvmInline
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


fun Path.stat(): StatResult = stat(this)

expect fun stat(path: Path): StatResult

sealed interface StatResult {
    sealed interface Error : StatResult {
        val message: String

        @JvmInline value class NotFound(override val message: String) : Error
        @JvmInline value class InvalidArgument(override val message: String) : Error
        @JvmInline value class Other(override val message: String) : Error
    }

    companion object
}

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
        ) {
            companion object {
                val None = Permissions(
                    canRead = false,
                    canWrite = false,
                    canExecute = false
                )
            }
        }

        companion object {
            val None = Mode(
                isBlockDevice = false,
                isCharacterDevice = false,
                isPipe = false,
                isRegularFile = false,
                isDirectory = false,
                isSymbolicLink = false,
                isSocket = false,
                user = Permissions.None,
                group = Permissions.None,
                others = Permissions.None
            )
        }
    }

    companion object {
        val None = Stat(
            deviceId = 0uL,
            serialNumber = 0uL,
            mode = Mode.None,
            userId = 0u,
            groupId = 0u,
            hardlinkCount = 0u,
            size = 0L,
            lastAccessTime = Instant.fromEpochMilliseconds(0L),
            lastModificationTime = Instant.fromEpochMilliseconds(0L),
            lastStatusChangeTime = Instant.fromEpochMilliseconds(0L)
        )
    }
}


infix fun UInt.mask(mask: Int): Boolean = (this and mask.toUInt()) != 0u
infix fun UShort.mask(mask: Int): Boolean = (this.toUInt() and mask.toUInt()) != 0u

infix fun UInt.bit(i: Int): Boolean = ((this shr i) and 1u) != 0u

fun Int.fullBinary(): String = (0..<Int.SIZE_BITS)
    .reversed()
    .map { (this ushr it) and 1 }
    .joinToString("")
