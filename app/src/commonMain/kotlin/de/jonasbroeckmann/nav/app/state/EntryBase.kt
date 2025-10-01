package de.jonasbroeckmann.nav.app.state

import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.StatResult
import de.jonasbroeckmann.nav.utils.error
import de.jonasbroeckmann.nav.utils.stat
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal abstract class EntryBase(override val path: Path) : Entry {
    private var statError: String? = null
    protected val statResult: StatResult by lazy {
        path.stat().also { statError = it.error?.message }
    }
    protected val stat get() = statResult as? Stat

    override val error: String? get() = statError

    override val type: Entry.Type get() = when {
        stat?.mode?.isSymbolicLink == true -> SymbolicLink
        stat?.mode?.isDirectory == true -> Directory
        stat?.mode?.isRegularFile == true -> RegularFile
        else -> Unknown
    }

    override val userPermissions get() = stat?.mode?.user?.toEntryPermissions()
    override val groupPermissions get() = stat?.mode?.group?.toEntryPermissions()
    override val othersPermissions get() = stat?.mode?.others?.toEntryPermissions()

    override val hardlinkCount get() = stat?.hardlinkCount

    override val userName: String? get() = null
    override val groupName: String? get() = null

    override val size get() = stat?.size?.takeIf { it >= 0 && type != Directory }

    override val lastModificationTime get() = stat?.lastModificationTime

    override val linkTarget: Entry.Link? get() = null
}

private fun Stat.Mode.Permissions.toEntryPermissions() = Entry.Permissions(
    canRead = canRead,
    canWrite = canWrite,
    canExecute = canExecute
)
