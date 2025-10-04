package de.jonasbroeckmann.nav.app.state

import de.jonasbroeckmann.nav.utils.ReadLinkResult
import de.jonasbroeckmann.nav.utils.error
import de.jonasbroeckmann.nav.utils.getGroupNameFromId
import de.jonasbroeckmann.nav.utils.getUserNameFromId
import de.jonasbroeckmann.nav.utils.readLink
import kotlinx.io.files.Path

actual fun Path.entry(): Entry = EntryImpl(this)

private data class EntryImpl(override val path: Path) : NativeEntry(path) {
    private var readLinkError: String? = null
    private val readLinkResult: ReadLinkResult? by lazy {
        if (type != SymbolicLink) return@lazy null
        readLink(path).also { readLinkError = it.error?.message }
    }

    override val error: String? get() = super.error ?: readLinkError

    override val isHidden: Boolean = path.name.startsWith(".")

    override val userName by lazy { stat?.userId?.let { getUserNameFromId(it) } }
    override val groupName by lazy { stat?.groupId?.let { getGroupNameFromId(it) } }

    override val linkTarget: Entry.Link? by lazy {
        when (val result = readLinkResult) {
            is ReadLinkResult.Success -> LinkImpl(result)
            is ReadLinkResult.Error, null -> null
        }
    }

    private data class LinkImpl(val result: ReadLinkResult.Success) : Entry.Link {
        override val path get() = result.value
        override val targetEntry by lazy { result.target.entry() }
    }
}
