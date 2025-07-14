package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.io.files.Path
import platform.posix.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)
actual fun stat(path: Path): Stat = memScoped {
    val result: stat = alloc()
    stat(path.toString(), result.ptr)
    return Stat(
        deviceId = result.st_dev,
        serialNumber = result.st_ino,
        mode = Stat.Mode(
            isBlockDevice = result.st_mode mask S_IFBLK,
            isCharacterDevice = result.st_mode mask S_IFCHR,
            isPipe = result.st_mode mask S_IFIFO,
            isRegularFile = result.st_mode mask S_IFREG,
            isDirectory = result.st_mode mask S_IFDIR,
            isSymbolicLink = result.st_mode mask S_IFLNK,
            isSocket = result.st_mode mask S_IFSOCK,
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
        userId = result.st_uid,
        groupId = result.st_gid,
        size = result.st_size,
        lastAccessTime = Instant.fromEpochSeconds(result.st_atim.tv_sec),
        lastModificationTime = Instant.fromEpochSeconds(result.st_mtim.tv_sec),
        lastStatusChangeTime = Instant.fromEpochSeconds(result.st_ctim.tv_sec)
    )
}
