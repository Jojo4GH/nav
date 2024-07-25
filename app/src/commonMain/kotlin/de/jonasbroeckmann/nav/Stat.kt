package de.jonasbroeckmann.nav

import kotlinx.datetime.Instant
import kotlinx.io.files.Path


expect fun stat(path: Path): Stat


data class Stat(
    val deviceId: ULong,
    val serialNumber: ULong,
    val mode: Mode,
    val userId: UInt,
    val groupId: UInt,
    val size: Long,
    val lastAccessTime: Instant,
    val lastModificationTime: Instant,
    val lastStatusChangeTime: Instant
) {
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


infix fun UInt.mask(mask: Int): Boolean = (this and mask.toUInt()) != 0u
infix fun UShort.mask(mask: Int): Boolean = (this.toUInt() and mask.toUInt()) != 0u

infix fun UInt.bit(i: Int): Boolean = ((this shr i) and 1u) != 0u

fun Int.fullBinary(): String = (0..<Int.SIZE_BITS)
    .reversed()
    .map { (this ushr it) and 1 }
    .joinToString("")
