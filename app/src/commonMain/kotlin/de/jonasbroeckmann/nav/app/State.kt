package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.Entry
import de.jonasbroeckmann.nav.entry
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import kotlinx.io.files.Path

data class State(
    val directory: Path,
    val items: List<Entry> = directory.entries(),
    val cursor: Int,
    val filter: String = "",

    private val menuCursor: Int = -1,
    private val allMenuActions: () -> List<MenuAction>,

    val command: String? = null,

    val inQuickMacroMode: Boolean = false,

    val lastReceivedEvent: KeyboardEvent? = null
) {
    val filteredItems: List<Entry> by lazy {
        if (filter.isEmpty()) return@lazy items
        items
            .filter { filter.lowercase() in it.path.name.lowercase() }
            .sortedByDescending { it.path.name.startsWith(filter) } // intentionally
    }
    val currentEntry: Entry? get() = filteredItems.getOrNull(cursor)

    val availableMenuActions get() = allMenuActions().filter { it.isAvailable(this) }
    val coercedMenuCursor get() = menuCursor.coerceAtMost(availableMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen get() = menuCursor >= 0
    val currentMenuAction get() = availableMenuActions.getOrNull(coercedMenuCursor)

    val isTypingCommand get() = command != null

    fun withMenuCursor(cursor: Int?) = copy(menuCursor = cursor?.coerceAtLeast(0) ?: -1)

    fun withCommand(command: String?) = copy(command = command)

    fun withCursor(cursor: Int) = copy(
        cursor = when {
            filteredItems.isEmpty() -> 0
            else -> cursor.mod(filteredItems.size)
        }
    )

    fun withCursorOnFirst(predicate: (Entry) -> Boolean): State = copy(
        cursor = filteredItems.indexOfFirst { predicate(it) }.coerceAtLeast(0)
    )

    fun withCursorOnNext(predicate: (Entry) -> Boolean): State = withCursorOnNextInOffsets(
        offsets = 1 until filteredItems.size,
        predicate = predicate
    )

    fun withCursorOnNextReverse(predicate: (Entry) -> Boolean): State = withCursorOnNextInOffsets(
        offsets = (1 until filteredItems.size).map { -it },
        predicate = predicate
    )

    private fun withCursorOnNextInOffsets(
        offsets: Iterable<Int>,
        predicate: (Entry) -> Boolean
    ): State {
        for (offset in offsets) {
            val i = (cursor + offset).mod(filteredItems.size)
            if (predicate(filteredItems[i])) {
                return copy(cursor = i)
            }
        }
        return this
    }

    fun filtered(filter: String): State {
        val tmp = copy(filter = filter)
        val newCursor = if (tmp.filteredItems.size < filteredItems.size) 0 else tmp.cursor
        return tmp.copy(items = tmp.items, cursor = newCursor)
    }

    fun navigatedUp(): State {
        val newDir = directory.parent ?: return this
        val entries = newDir.entries()
        return copy(
            directory = newDir,
            items = entries,
            cursor = entries.indexOfFirst { it.path.name == directory.name }.coerceAtLeast(0),
            filter = ""
        )
    }

    fun navigatedInto(entry: Entry?): State {
        return when {
            entry == null -> this
            entry.type == Directory || entry.linkTarget?.targetEntry?.type == Directory -> copy(
                directory = entry.path,
                items = entry.path.entries(),
                cursor = 0,
                filter = ""
            )
            else -> this
        }
    }

    fun updatedEntries(preferredEntry: String? = currentEntry?.path?.name): State {
        val tmp = copy(items = directory.entries())
        return when (preferredEntry) {
            null -> tmp.copy(cursor = 0)
            else -> tmp.withCursorOnFirst { it.path.name == preferredEntry }
        }
    }

    fun inQuickMacroMode(enabled: Boolean = true) = when (enabled) {
        true -> copy(inQuickMacroMode = true).withMenuCursor(null)
        false -> copy(inQuickMacroMode = false)
    }

    companion object {
        private fun Path.entries(): List<Entry> = children()
            .asSequence()
            .map { it.cleaned() } // fix broken paths
            .map { it.entry() }
            .sortedBy { it.path.name }
            .sortedByDescending { it.type == Directory }
            .toList()
    }
}
