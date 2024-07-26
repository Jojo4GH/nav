package de.jonasbroeckmann.nav

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.datetime.Instant
import kotlinx.io.files.Path
import platform.posix.*


@OptIn(ExperimentalForeignApi::class)
actual fun stat(path: Path): Stat = memScoped {
    val result: stat = alloc()
    stat(path.toString(), result.ptr)
    return Stat(
        deviceId = result.st_dev.toULong(),
        serialNumber = result.st_ino.toULong(),
        mode = Stat.Mode(
            isBlockDevice = result.st_mode mask S_IFBLK,
            isCharacterDevice = result.st_mode mask S_IFCHR,
            isPipe = result.st_mode mask S_IFIFO,
            isRegularFile = result.st_mode mask S_IFREG,
            isDirectory = result.st_mode mask S_IFDIR,
            isSymbolicLink = false,
            isSocket = false,
            user = Stat.Mode.Permissions(
                canRead = result.st_mode mask S_IRUSR,
                canWrite = result.st_mode mask S_IWUSR,
                canExecute = result.st_mode mask S_IXUSR
            ),
            group = Stat.Mode.Permissions(
                canRead = result.st_mode mask S_IRGRP,
                canWrite = result.st_mode mask S_IWGRP,
                canExecute = result.st_mode mask S_IXGRP
            ),
            others = Stat.Mode.Permissions(
                canRead = result.st_mode mask S_IROTH,
                canWrite = result.st_mode mask S_IWOTH,
                canExecute = result.st_mode mask S_IXOTH
            )
        ),
        userId = result.st_uid.toUInt(),
        groupId = result.st_gid.toUInt(),
        size = result.st_size.toLong(),
        lastAccessTime = Instant.fromEpochSeconds(result.st_atime),
        lastModificationTime = Instant.fromEpochSeconds(result.st_mtime),
        lastStatusChangeTime = Instant.fromEpochSeconds(result.st_ctime)
    )
}
