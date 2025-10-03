package de.jonasbroeckmann.nav.app.state

import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

expect fun Path.entry(): Entry

@OptIn(ExperimentalTime::class)
interface Entry {
    /**
     * This must always be an absolute path
     */
    val path: Path

    val error: String?

    val type: Type

    val isHidden: Boolean?

    val userPermissions: Permissions?
    val groupPermissions: Permissions?
    val othersPermissions: Permissions?

    val hardlinkCount: UInt?

    val userName: String?
    val groupName: String?

    val size: Long?

    val lastModificationTime: Instant?

    val linkTarget: Link?

    data class Permissions(
        val canRead: Boolean = false,
        val canWrite: Boolean = false,
        val canExecute: Boolean = false
    )

    enum class Type {
        Directory,
        RegularFile,
        SymbolicLink,
        Unknown
    }

    interface Link {
        /**
         * This might be relative or absolute
         */
        val path: Path
        val targetEntry: Entry
    }
}
