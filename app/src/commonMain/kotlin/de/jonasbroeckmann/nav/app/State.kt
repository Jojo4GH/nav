package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.StatResult
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.stat
import kotlinx.io.files.Path

data class State(
    val initialDirectory: Path,
    val directory: Path,
    val items: List<Entry> = directory.entries(),
    val cursor: Int,
    val filter: String = "",

    private val menuCursor: Int = -1,
    private val allMenuActions: () -> List<MenuAction>,

    val command: String? = null,

    val inQuickMacroMode: Boolean = false,

    val debugMode: Boolean = false,
    val lastReceivedEvent: KeyboardEvent? = null
) {
    val hasFilter get() = filter.isNotEmpty()
    val filteredItems: List<Entry> by lazy {
        if (!hasFilter) return@lazy items
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
        if (entry == null || !entry.isDirectory) return this
        return copy(
            directory = entry.path,
            items = entry.path.entries(),
            cursor = 0,
            filter = ""
        )
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
            .map { Entry(it, it.stat()) }
            .sortedBy { it.path.name }
            .sortedByDescending { it.isDirectory }
            .toList()
    }


    data class Entry(
        val path: Path,
        val stat: Stat,
        val statError: StatResult.Error?
    ) {
        constructor(path: Path, statResult: StatResult) : this(
            path = path,
            stat = statResult as? Stat ?: Stat.None,
            statError = statResult as? StatResult.Error
        )

        val isDirectory get() = stat.mode.isDirectory
        val isRegularFile get() = stat.mode.isRegularFile
        val isSymbolicLink get() = stat.mode.isSymbolicLink
        val size get() = stat.size.takeIf { it >= 0 && !isDirectory }
    }
}

