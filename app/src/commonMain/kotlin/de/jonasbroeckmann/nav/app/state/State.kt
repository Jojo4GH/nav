package de.jonasbroeckmann.nav.app.state

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.app.actions.MenuActions
import de.jonasbroeckmann.nav.framework.semantics.FilterableItemList
import de.jonasbroeckmann.nav.framework.semantics.FilterableItemListSemantics
import de.jonasbroeckmann.nav.framework.semantics.NavigableItemList
import de.jonasbroeckmann.nav.framework.semantics.NavigableItemListSemantics
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.isDirectory
import kotlinx.io.files.Path

data class State private constructor(
    val directory: Path,
    override val unfilteredItems: List<Entry> = directory.entries(),
    override val cursor: Int,
    override val filter: String = "",
    val showHiddenEntries: Boolean,

    private val menuCursor: Int = -1,
    private val menuActions: MenuActions,

    val command: String? = null,

    val inputMode: InputMode? = null,

    val lastReceivedEvent: KeyboardEvent? = null
) : StateProvider, FilterableItemList<State, Entry>, NavigableItemList<State, Entry> {
    override val state get() = this

    private val filterableItemListSemantics = FilterableItemListSemantics(
        self = this,
        lazyItems = { unfilteredItems },
        filter = filter,
        copyWithFilter = { copy(filter = it) },
        filterOn = { path.name },
        preSort = {
            if (!showHiddenEntries) {
                sortedByDescending { it.isHidden != true }
            } else {
                this
            }
        },
        onNoFilter = {
            if (!showHiddenEntries) {
                filter { it.isHidden != true } // only remove hidden entries if filter is empty
            } else {
                this
            }
        }
    )

    override val filteredItems get() = filterableItemListSemantics.filteredItems

    override fun withFilter(filter: String) = filterableItemListSemantics.withFilter(filter)

    private val navigableItemListSemantics = NavigableItemListSemantics(
        self = this,
        lazyItems = { filteredItems },
        cursor = cursor,
        copyWithCursor = { copy(cursor = it) }
    )

    override val currentItem get() = navigableItemListSemantics.currentItem

    override fun withCursorCoerced(cursor: Int) = navigableItemListSemantics.withCursorCoerced(cursor)

    override fun withCursorShifted(offset: Int) = navigableItemListSemantics.withCursorShifted(offset)

    override fun withCursorOnFirst(
        default: Int,
        predicate: (Entry) -> Boolean
    ) = navigableItemListSemantics.withCursorOnFirst(default, predicate)

    override fun withCursorOnNext(predicate: (Entry) -> Boolean) = navigableItemListSemantics.withCursorOnNext(predicate)

    override fun withCursorOnNextReverse(predicate: (Entry) -> Boolean) = navigableItemListSemantics.withCursorOnNextReverse(predicate)

    val shownMenuActions get() = menuActions.all.filter { it.isShown() }
    val coercedMenuCursor get() = menuCursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen get() = menuCursor >= 0
    val currentMenuAction get() = shownMenuActions.getOrNull(coercedMenuCursor)

    val inQuickMacroMode get() = inputMode == InputMode.QuickMacro

    val isTypingCommand get() = command != null

    fun withMenuCursorCoerced(cursor: Int) = copy(
        menuCursor = cursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    )

    fun withCommand(command: String?) = copy(command = command)

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

    fun withInputMode(inputMode: InputMode) = copy(inputMode = inputMode)

//    fun inQuickMacroMode(enabled: Boolean = true) =

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
            menuActions: MenuActions
        ) = State(
            directory = startingDirectory,
            cursor = 0,
            showHiddenEntries = showHiddenEntries,
            menuActions = menuActions
        )
    }
}
