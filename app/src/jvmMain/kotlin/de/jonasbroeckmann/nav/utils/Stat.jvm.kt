package de.jonasbroeckmann.nav.utils

import kotlinx.datetime.Instant
import kotlinx.io.files.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

actual fun stat(path: Path): Stat {
    // TODO: Implement stat for JVM
    val javaPath = java.nio.file.Path.of(path.toString())
//    val permissions = javaPath.getPosixFilePermissions()
    val permissions = emptySet<PosixFilePermission>()
    return Stat(
        deviceId = 0u,
        serialNumber = 0u,
        mode = Stat.Mode(
            isBlockDevice = false,
            isCharacterDevice = false,
            isPipe = false,
            isRegularFile = javaPath.isRegularFile(),
            isDirectory = javaPath.isDirectory(),
            isSymbolicLink = javaPath.isSymbolicLink(),
            isSocket = false,
            user = Stat.Mode.Permissions(
                canRead = PosixFilePermission.OWNER_READ in permissions,
                canWrite = PosixFilePermission.OWNER_WRITE in permissions,
                canExecute = PosixFilePermission.OWNER_EXECUTE in permissions
            ),
            group = Stat.Mode.Permissions(
                canRead = PosixFilePermission.GROUP_READ in permissions,
                canWrite = PosixFilePermission.GROUP_WRITE in permissions,
                canExecute = PosixFilePermission.GROUP_EXECUTE in permissions
            ),
            others = Stat.Mode.Permissions(
                canRead = PosixFilePermission.OTHERS_READ in permissions,
                canWrite = PosixFilePermission.OTHERS_WRITE in permissions,
                canExecute = PosixFilePermission.OTHERS_EXECUTE in permissions
            )
        ),
        userId = 0u,
        groupId = 0u,
        size = javaPath.fileSize(),
        lastAccessTime = javaPath.getAttribute("basic:lastAccessTime").parseFileTime(),
        lastModificationTime = javaPath.getAttribute("basic:lastModifiedTime").parseFileTime(),
        lastStatusChangeTime = Instant.DISTANT_PAST
    )
}

private fun Any?.parseFileTime() = when (this) {
    is FileTime -> Instant.fromEpochSeconds(toInstant().epochSecond)
    else -> Instant.DISTANT_PAST
}
