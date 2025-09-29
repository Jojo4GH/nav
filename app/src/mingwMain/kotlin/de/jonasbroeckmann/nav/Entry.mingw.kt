package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.FileAttributes
import de.jonasbroeckmann.nav.utils.FileAttributesResult
import de.jonasbroeckmann.nav.utils.FinalPathResult
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.error
import de.jonasbroeckmann.nav.utils.fileAttributes
import de.jonasbroeckmann.nav.utils.finalPath
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime

actual fun Path.entry(): Entry = EntryImpl(this)

@OptIn(ExperimentalTime::class)
private data class EntryImpl(override val path: Path) : EntryBase(path) {
    private var fileAttributesError: String? = null
    private val fileAttributesResult: FileAttributesResult by lazy {
        path.fileAttributes().also { fileAttributesError = it.error?.message }
    }
    private val fileAttributes get() = fileAttributesResult as? FileAttributes

    private var finalPathError: String? = null
    private val finalPathResult: FinalPathResult? by lazy {
        if (type != SymbolicLink) return@lazy null
        path.finalPath().also { finalPathError = it.error?.message }
    }

    override val error: String? get() = super.error ?: fileAttributesError

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

    override val linkTarget: Entry.Link? by lazy {
        when (val result = finalPathResult) {
            is FinalPathResult.Success -> object : Entry.Link {
                override val path get() = result.path
                override val targetEntry by lazy { result.path.absolute().entry() }
            }
            is FinalPathResult.Error, null -> null
        }
    }
}
