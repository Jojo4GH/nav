@file:Suppress("detekt:Filename", "detekt:MatchingDeclarationName")

package de.jonasbroeckmann.nav.app.state

import de.jonasbroeckmann.nav.app.state.Entry.Type.Directory
import de.jonasbroeckmann.nav.app.state.Entry.Type.RegularFile
import de.jonasbroeckmann.nav.app.state.Entry.Type.SymbolicLink
import de.jonasbroeckmann.nav.app.state.Entry.Type.Unknown
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.StatResult
import de.jonasbroeckmann.nav.utils.error
import de.jonasbroeckmann.nav.utils.stat
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal abstract class NativeEntry(override val path: Path) : Entry {
    private var statError: String? = null
    protected val statResult: StatResult by lazy {
        stat(path).also { statError = it.error?.message }
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

    override val size get() = stat?.size?.takeIf { it >= 0 && type != Directory }

    override val lastModificationTime get() = stat?.lastModificationTime
}

private fun Stat.Mode.Permissions.toEntryPermissions() = Entry.Permissions(
    canRead = canRead,
    canWrite = canWrite,
    canExecute = canExecute
)
