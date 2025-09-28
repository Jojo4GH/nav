package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.StatResult
import de.jonasbroeckmann.nav.utils.getGroupNameFromId
import de.jonasbroeckmann.nav.utils.getUserNameFromId
import de.jonasbroeckmann.nav.utils.stat
import kotlinx.io.files.Path

class Entry(val path: Path) {
    private val statResult: StatResult by lazy { path.stat() }
    private val statError get() = statResult as? StatResult.Error

    val stat get() = statResult as? Stat ?: Stat.None
    val isDirectory get() = stat.mode.isDirectory
    val isRegularFile get() = stat.mode.isRegularFile
    val isSymbolicLink get() = stat.mode.isSymbolicLink
    val size get() = stat.size.takeIf { it >= 0 && !isDirectory }

    val userName by lazy { getUserNameFromId(stat.userId) }
    val groupName by lazy { getGroupNameFromId(stat.groupId) }

    val error get() = statError?.message
}
