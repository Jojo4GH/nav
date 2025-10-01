package de.jonasbroeckmann.nav.app.state

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.MenuAction
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import kotlinx.io.files.Path

data class State private constructor(
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
        val lowercaseFilter = filter.lowercase()
        items
            .filter { lowercaseFilter in it.path.name.lowercase() }
            // TODO replace with scoring algorithm
            .sortedByDescending { it.path.name.startsWith(filter, ignoreCase = true) }
            .sortedByDescending { it.path.name.startsWith(filter) }
    }
    val currentEntry: Entry? get() = filteredItems.getOrNull(cursor)

    val availableMenuActions get() = allMenuActions().filter { it.isAvailable(this) }
    val coercedMenuCursor get() = menuCursor.coerceAtMost(availableMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen get() = menuCursor >= 0
    val currentMenuAction get() = availableMenuActions.getOrNull(coercedMenuCursor)

    val isTypingCommand get() = command != null

    fun withMenuCursor(cursor: Int?) = copy(menuCursor = cursor?.coerceAtLeast(0) ?: -1)

    fun withCommand(command: String?) = copy(command = command)

    fun withCursorCoerced(cursor: Int) = copy(
        cursor = cursor.coerceAtMost(filteredItems.lastIndex).coerceAtLeast(0)
    )

    fun withCursorShifted(offset: Int) = withCursorCoerced(
        cursor = when {
            filteredItems.isEmpty() -> 0
            else -> (cursor + offset).mod(filteredItems.size)
        }
    )

    fun withCursorOn(preferredEntry: String?, default: Int = cursor) = when (preferredEntry) {
        null -> withCursorCoerced(default)
        else -> withCursorOnFirst(default = default) { it.path.name == preferredEntry }
    }

    fun withCursorOnFirst(default: Int = cursor, predicate: (Entry) -> Boolean): State = withCursorCoerced(
        cursor = filteredItems.indexOfFirst { predicate(it) }.takeIf { it >= 0 } ?: default
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

    fun withFilter(filter: String): State {
        val tmp = copy(filter = filter)
        return if (filteredItems.size > tmp.filteredItems.size) {
            // if we filtered something out, move the cursor to the best match
            tmp.withCursorCoerced(0)
        } else {
            // otherwise try to stay on the same entry
            tmp.withCursorOn(currentEntry?.path?.name)
        }
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
        return copy(items = directory.entries()).withCursorOn(preferredEntry)
    }

    fun inQuickMacroMode(enabled: Boolean = true) = when (enabled) {
        true -> copy(inQuickMacroMode = true).withMenuCursor(null)
        false -> copy(inQuickMacroMode = false)
    }

    fun withLastReceivedEvent(event: KeyboardEvent?) = copy(lastReceivedEvent = event)

    companion object {
        private fun Path.entries(): List<Entry> = children()
            .asSequence()
            .map { it.cleaned() } // fix broken paths
            .map { it.entry() }
            .sortedBy { it.path.name }
            .sortedByDescending { it.type == Directory }
            .toList()

        fun initial(
            startingDirectory: Path,
            allMenuActions: () -> List<MenuAction>
        ) = State(
            directory = startingDirectory,
            cursor = 0,
            allMenuActions = allMenuActions
        )
    }
}
