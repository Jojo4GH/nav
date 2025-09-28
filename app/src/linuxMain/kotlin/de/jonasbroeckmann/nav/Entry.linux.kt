package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.getGroupNameFromId
import de.jonasbroeckmann.nav.utils.getUserNameFromId
import kotlinx.io.files.Path

actual fun Path.entry(): Entry = EntryImpl(this)

private data class EntryImpl(override val path: Path) : EntryBase(path) {
    override val userName by lazy { stat?.userId?.let { getUserNameFromId(it) } }
    override val groupName by lazy { stat?.groupId?.let { getGroupNameFromId(it) } }
}
