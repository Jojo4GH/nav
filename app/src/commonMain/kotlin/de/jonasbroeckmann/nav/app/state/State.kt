package de.jonasbroeckmann.nav.app.state

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.actions.MenuAction
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.isDirectory
import kotlinx.io.files.Path

data class State private constructor(
    val directory: Path,
    val unfilteredItems: List<Entry> = directory.entries(),
    val cursor: Int,
    val filter: String = "",
    val showHiddenEntries: Boolean,

    private val menuCursor: Int = -1,
    private val allMenuActions: () -> List<MenuAction>,

    val command: String? = null,

    val inQuickMacroMode: Boolean = false,

    val lastReceivedEvent: KeyboardEvent? = null
) : StateProvider {
    override val state get() = this

    val filteredItems: List<Entry> by lazy {
        when {
            filter.isNotEmpty() -> {
                val lowercaseFilter = filter.lowercase()
                unfilteredItems
                    .filter { lowercaseFilter in it.path.name.lowercase() }
                    // TODO replace with scoring algorithm
                    .let { entries ->
                        if (!showHiddenEntries) {
                            entries.sortedByDescending { it.isHidden != true }
                        } else {
                            entries
                        }
                    }
                    .sortedByDescending { it.path.name.startsWith(filter, ignoreCase = true) }
                    .sortedByDescending { it.path.name.startsWith(filter) }
            }
            !showHiddenEntries -> unfilteredItems.filter { it.isHidden != true } // only remove hidden entries if filter is empty
            else -> unfilteredItems
        }
    }
    val currentItem: Entry? get() = filteredItems.getOrNull(cursor)

    val shownMenuActions get() = allMenuActions().filter { it.isShown() }
    val coercedMenuCursor get() = menuCursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen get() = menuCursor >= 0
    val currentMenuAction get() = shownMenuActions.getOrNull(coercedMenuCursor)

    val isTypingCommand get() = command != null

    fun withMenuCursorCoerced(cursor: Int) = copy(
        menuCursor = cursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    )

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
            tmp.withCursorOnFirst { it.path.name == currentItem?.path?.name }
        }
    }

    fun navigateTo(path: Path?): State {
        if (path == null) return this
        if (directory == path) return this
        if (!path.isDirectory) return this

        tailrec fun Path.nearestChildToOrNull(parent: Path): Path? {
            if (this.parent == parent) return this
            return this.parent?.nearestChildToOrNull(parent)
        }

        val nearestChild = directory.nearestChildToOrNull(parent = path)
        return if (nearestChild != null) {
            // navigating to a parent directory, try to stay on the same entry
            val entries = path.entries()
            copy(
                directory = path,
                unfilteredItems = entries,
                cursor = entries.indexOfFirst { it.path.name == nearestChild.name }.coerceAtLeast(0),
                filter = ""
            )
        } else {
            // navigating to an unrelated directory, go to the top
            copy(
                directory = path,
                unfilteredItems = path.entries(),
                cursor = 0,
                filter = ""
            )
        }
    }

    fun navigatedUp() = navigateTo(directory.parent)

    fun updatedEntries(preferredEntry: (Entry) -> Boolean = { it.path.name == currentItem?.path?.name }): State {
        return copy(unfilteredItems = directory.entries()).withCursorOnFirst(predicate = preferredEntry)
    }

    fun inQuickMacroMode(enabled: Boolean = true) = when (enabled) {
        true -> copy(inQuickMacroMode = true).withMenuCursorCoerced(-1)
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
            showHiddenEntries: Boolean,
            allMenuActions: () -> List<MenuAction>
        ) = State(
            directory = startingDirectory,
            cursor = 0,
            showHiddenEntries = showHiddenEntries,
            allMenuActions = allMenuActions
        )
    }
}
