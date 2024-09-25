package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.stat
import kotlinx.io.files.Path

data class State(
    val directory: Path,
    val items: List<Entry> = directory.entries(),
    val cursor: Int,
    val filter: String = "",
    private val menuCursor: Int = -1,
    private val allMenuActions: () -> List<MenuAction>,
//    val exit: Path? = null,
    val debugMode: Boolean = false,
    val lastReceivedEvent: KeyboardEvent? = null
) {
    val filteredItems: List<Entry> by lazy {
        if (filter.isEmpty()) return@lazy items
        items.filter { filter.lowercase() in it.path.name.lowercase() }
            .sortedByDescending { it.path.name.startsWith(filter) }
    }
    val currentEntry: Entry? get() = filteredItems.getOrNull(cursor)

    val availableMenuActions get() = allMenuActions().filter { it.isAvailable(this) }
    val coercedMenuCursor get() = menuCursor.coerceAtMost(availableMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen get() = menuCursor >= 0
    val currentMenuAction get() = availableMenuActions.getOrNull(coercedMenuCursor)

    fun withMenuCursor(cursor: Int?) = copy(menuCursor = cursor?.coerceAtLeast(0) ?: -1)

    fun withCursor(cursor: Int) = copy(
        cursor = when {
            filteredItems.isEmpty() -> 0
            else -> cursor.mod(filteredItems.size)
        }
    )

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
        if (entry == null || !entry.isDirectory) return this
        return copy(
            directory = entry.path,
            items = entry.path.entries(),
            cursor = 0,
            filter = ""
        )
    }

    fun updatedEntries(preferredEntry: String? = currentEntry?.path?.name): State {
        val entries = directory.entries()
        val tmp = copy(items = entries)
        return tmp.copy(
            cursor = when {
                preferredEntry != null -> tmp.filteredItems.indexOfFirst { it.path.name == preferredEntry }.coerceAtLeast(0)
                else -> 0
            }
        )
    }

    companion object {
        private fun Path.entries(): List<Entry> = children()
            .map { it.cleaned() } // fix broken paths
            .map { Entry(it, it.stat()) }
            .sortedBy { it.path.name }
            .sortedByDescending { it.isDirectory }
    }


    data class Entry(
        val path: Path,
        val stat: Stat
    ) {
        val isDirectory get() = stat.mode.isDirectory
        val isRegularFile get() = stat.mode.isRegularFile
        val isSymbolicLink get() = stat.mode.isSymbolicLink
        val size get() = stat.size.takeIf { it >= 0 && !isDirectory }
    }
}

