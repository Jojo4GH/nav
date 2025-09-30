package de.jonasbroeckmann.nav

interface Colors {
    val path: String
    val filter: String
    val filterMarker: String
    val keyHints: String

    val permissionRead: String
    val permissionWrite: String
    val permissionExecute: String
    val hardlinkCount: String
    val user: String
    val group: String
    val entrySize: String
    val modificationTime: String

    val directory: String
    val file: String
    val link: String

    companion object {
        operator fun invoke(
            path: String,
            filter: String,
            filterMarker: String,
            keyHints: String,
            permissionRead: String,
            permissionWrite: String,
            permissionExecute: String,
            hardlinkCount: String,
            user: String,
            group: String,
            entrySize: String,
            modificationTime: String,
            directory: String,
            file: String,
            link: String
        ): Colors = Impl(
            path = path,
            filter = filter,
            filterMarker = filterMarker,
            keyHints = keyHints,
            permissionRead = permissionRead,
            permissionWrite = permissionWrite,
            permissionExecute = permissionExecute,
            hardlinkCount = hardlinkCount,
            user = user,
            group = group,
            entrySize = entrySize,
            modificationTime = modificationTime,
            directory = directory,
            file = file,
            link = link
        )
    }

    private data class Impl(
        override val path: String,
        override val filter: String,
        override val filterMarker: String,
        override val keyHints: String,

        override val permissionRead: String,
        override val permissionWrite: String,
        override val permissionExecute: String,
        override val hardlinkCount: String,
        override val user: String,
        override val group: String,
        override val entrySize: String,
        override val modificationTime: String,

        override val directory: String,
        override val file: String,
        override val link: String
    ) : Colors
}
