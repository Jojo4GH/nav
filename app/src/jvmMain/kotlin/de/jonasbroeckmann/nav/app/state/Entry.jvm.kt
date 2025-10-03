package de.jonasbroeckmann.nav.app.state

import kotlinx.io.files.Path
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import kotlin.getValue
import kotlin.io.path.getAttribute
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import java.nio.file.Path as JavaPath

actual fun Path.entry(): Entry = EntryImpl(this)

@OptIn(ExperimentalTime::class)
private class EntryImpl(override val path: Path) : Entry {
    private var javaError: String? = null
    private val javaPath by lazyCatching(default = { null }) { JavaPath.of(path.toString()) }

    override val error: String? get() = javaError

    override val type: Entry.Type by javaPath(default = { Unknown }) {
        when {
            isSymbolicLink() -> SymbolicLink
            isDirectory(NOFOLLOW_LINKS) -> Entry.Type.Directory
            isRegularFile(NOFOLLOW_LINKS) -> RegularFile
            else -> Unknown
        }
    }

    override val isHidden by javaPath { isHidden() }

    private val posixFilePermission by javaPath { getPosixFilePermissions(NOFOLLOW_LINKS) }
    override val userPermissions by lazy {
        posixFilePermission?.let {
            Entry.Permissions(
                canRead = PosixFilePermission.OWNER_READ in it,
                canWrite = PosixFilePermission.OWNER_WRITE in it,
                canExecute = PosixFilePermission.OWNER_EXECUTE in it
            )
        }
    }
    override val groupPermissions by lazy {
        posixFilePermission?.let {
            Entry.Permissions(
                canRead = PosixFilePermission.GROUP_READ in it,
                canWrite = PosixFilePermission.GROUP_WRITE in it,
                canExecute = PosixFilePermission.GROUP_EXECUTE in it
            )
        }
    }
    override val othersPermissions by lazy {
        posixFilePermission?.let {
            Entry.Permissions(
                canRead = PosixFilePermission.OTHERS_READ in it,
                canWrite = PosixFilePermission.OTHERS_WRITE in it,
                canExecute = PosixFilePermission.OTHERS_EXECUTE in it
            )
        }
    }

    override val hardlinkCount = 0u

    override val userName = null
    override val groupName = null

    override val size by javaPath { Files.size(this) }

    override val lastModificationTime by javaPath { getAttribute("basic:lastModifiedTime").parseFileTime() }

    override val linkTarget = null

    private fun <R> javaPath(
        block: JavaPath.() -> R
    ): Lazy<R?> = lazyCatching(default = { null }) { javaPath?.block() }

    private fun <R> javaPath(
        default: () -> R,
        block: JavaPath.() -> R
    ) = lazyCatching(default = default) { javaPath?.block() ?: default() }

    private fun <R> lazyCatching(default: () -> R, block: () -> R) = lazy {
        try {
            return@lazy block()
        } catch (e: Exception) {
            javaError = e.message ?: e::class.simpleName
            return@lazy default()
        }
    }

    private fun Any?.parseFileTime() = when (this) {
        is FileTime -> Instant.fromEpochSeconds(toInstant().epochSecond)
        else -> Instant.DISTANT_PAST
    }
}
