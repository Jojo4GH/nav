package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.FileAttributes
import de.jonasbroeckmann.nav.utils.FileAttributesResult
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.error
import de.jonasbroeckmann.nav.utils.fileAttributes
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime

actual fun Path.entry(): Entry = EntryImpl(this)

@OptIn(ExperimentalTime::class)
private data class EntryImpl(override val path: Path) : EntryBase(path) {
    private val fileAttributesResult: FileAttributesResult by lazy { path.fileAttributes() }
    private val fileAttributes get() = fileAttributesResult as? FileAttributes

    override val error: String? get() = statResult.error?.message ?: fileAttributesResult.error?.message

    override val type: Entry.Type = when {
        fileAttributes?.isReparsePoint == true -> SymbolicLink
        fileAttributes?.isDirectory == true -> Directory
        stat?.mode?.isRegularFile == true -> RegularFile
        else -> Unknown
    }

    private fun Stat.Mode.Permissions.convert() = Entry.Permissions(
        canRead = canRead,
        canWrite = canWrite && fileAttributes?.isReadOnly != true,
        canExecute = canExecute
    )

    override val userPermissions by lazy { stat?.mode?.user?.convert() }
    override val groupPermissions by lazy { stat?.mode?.group?.convert() }
    override val othersPermissions by lazy { stat?.mode?.others?.convert() }
}
