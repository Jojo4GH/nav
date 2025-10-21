package de.jonasbroeckmann.nav.app.state

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.actions.MenuActions
import de.jonasbroeckmann.nav.app.actions.NormalModeActions
import de.jonasbroeckmann.nav.app.actions.QuickMacroModeActions
import de.jonasbroeckmann.nav.app.state.Entry.Type.Directory
import de.jonasbroeckmann.nav.framework.action.MenuAction
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.framework.semantics.FilterableItemList
import de.jonasbroeckmann.nav.framework.semantics.NavigableItemList
import de.jonasbroeckmann.nav.framework.semantics.scoreFor
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.isDirectory
import kotlinx.io.files.Path
import kotlin.text.isNotEmpty

data class FilterableNavigableItemListState<Item> private constructor(
    override val unfilteredItems: List<Item>,
    override val filter: String,
    val filterOn: Item.() -> String,
    val hiddenOn: (Item.() -> Boolean?)?,
    override val filteredItems: List<Item>
) : FilterableItemList<FilterableNavigableItemListState<Item>, Item> {

    fun with(unfilteredItems: List<Item>, filter: String): FilterableNavigableItemListState<Item> {
        val filteredItems = computeFiltered(
            items = unfilteredItems,
            filter = filter,
            filterOn = filterOn,
            hiddenOn = hiddenOn
        )
        return copy(
            unfilteredItems = unfilteredItems,
            filter = filter,
            filteredItems = filteredItems
        )
    }

    override fun withFilter(filter: String): FilterableNavigableItemListState<Item> {
        if (filter == this.filter) return this
        val filteredItems = computeFiltered(
            items = if (filter.startsWith(this.filter)) {
                filteredItems // incremental filtering for better performance
            } else {
                unfilteredItems
            },
            filter = filter,
            filterOn = filterOn,
            hiddenOn = hiddenOn
        )
        return copy(
            filter = filter,
            filteredItems = filteredItems
        )
    }

    companion object {
        fun <Item> initial(
            unfilteredItems: List<Item>,
            filter: String,
            filterOn: Item.() -> String,
            hiddenOn: (Item.() -> Boolean?)?
        ): FilterableNavigableItemListState<Item> {
            val filteredItems = computeFiltered(
                items = unfilteredItems,
                filter = filter,
                filterOn = filterOn,
                hiddenOn = hiddenOn
            )
            return FilterableNavigableItemListState(
                unfilteredItems = unfilteredItems,
                filter = filter,
                filterOn = filterOn,
                hiddenOn = hiddenOn,
                filteredItems = filteredItems
            )
        }

        private fun <Item> computeFiltered(
            items: List<Item>,
            filter: String,
            filterOn: Item.() -> String,
            hiddenOn: (Item.() -> Boolean?)?
        ): List<Item> = when {
            filter.isNotEmpty() -> {
                val lowercaseFilter = filter.lowercase()
                items
                    .mapNotNull { item ->
                        item.filterOn().scoreFor(lowercaseFilter)
                            .takeIf { it > 0.0 }
                            ?.let { item to it }
                    }
                    .let { items ->
                        if (hiddenOn != null) {
                            items.sortedByDescending { (item, _) -> item.hiddenOn() != true }
                        } else {
                            items
                        }
                    }
                    .sortedByDescending { (_, score) -> score }
                    .map { (item, _) -> item }
            }
            else -> {
                if (hiddenOn != null) {
                    items.filter { it.hiddenOn() != true } // only remove hidden entries if filter is empty
                } else {
                    items
                }
            }
        }
    }
}

data class NavigableItemListState<Item> private constructor(
    val items: List<Item>,
    override val cursor: Int,
    val filterOn: Item.() -> String,
) : NavigableItemList<NavigableItemListState<Item>, Item> {
    override val currentItem by lazy { items.getOrNull(cursor) }

    fun withItems(items: List<Item>): NavigableItemListState<Item> {
        return if (items.size < this.items.size) {
            // if we filtered something out, move the cursor to the best match
            copy(
                items = items,
                cursor = 0
            )
        } else {
            // otherwise try to stay on the same entry
            copy(
                items = items,
                cursor = items
                    .indexOfFirst { it.filterOn() == currentItem?.filterOn() }
                    .takeIf { it >= 0 }
                    ?: cursor
            )
        }
    }

    override fun withCursor(cursor: Int): NavigableItemListState<Item> {
        val cursorCoerced = cursor.coerceAtMost(items.lastIndex).coerceAtLeast(0)
        return if (cursorCoerced != this.cursor) copy(cursor = cursorCoerced) else this
    }

    override fun withCursorShifted(offset: Int) = withCursor(
        cursor = when {
            items.isEmpty() -> 0
            else -> (cursor + offset).mod(items.size)
        }
    )

    override fun withCursorOnFirst(default: Int, predicate: (Item) -> Boolean) = withCursor(
        cursor = items.indexOfFirst { predicate(it) }.takeIf { it >= 0 } ?: default
    )

    override fun withCursorOnNext(predicate: (Item) -> Boolean) = withCursorOnNextInOffsets(
        offsets = 1 until items.size,
        predicate = predicate
    )

    override fun withCursorOnNextReverse(predicate: (Item) -> Boolean) = withCursorOnNextInOffsets(
        offsets = (1 until items.size).map { -it },
        predicate = predicate
    )

    private fun withCursorOnNextInOffsets(
        offsets: Iterable<Int>,
        predicate: (Item) -> Boolean
    ): NavigableItemListState<Item> {
        for (offset in offsets) {
            val i = (cursor + offset).mod(items.size)
            if (predicate(items[i])) {
                return withCursor(i)
            }
        }
        return withCursor(cursor)
    }

    companion object {
        fun <Item> initial(
            items: List<Item>,
            cursor: Int,
            filterOn: Item.() -> String
        ) = NavigableItemListState(
            items = items,
            cursor = cursor,
            filterOn = filterOn
        )
    }
}


data class State private constructor(
    val directory: Path,

    private val filterableNavigableItems: FilterableNavigableItemListState<Entry>,
    private val navigableItems: NavigableItemListState<Entry>,

    private val rawMenuCursor: Int,

    val command: String?,

    val inQuickMacroMode: Boolean,

    val normalModeActions: NormalModeActions,
    val quickMacroModeActions: QuickMacroModeActions,
    val menuActions: MenuActions,

    val inputMode: InputMode?,

    val dialog: Widget?,

    val lastReceivedEvent: KeyboardEvent?
) : StateProvider, FilterableItemList<State, Entry>, NavigableItemList<State, Entry> {
    override val state get() = this

    private fun withDirectory(directory: Path) = copy(directory = directory)
        .withFilterableItems(
            filterableNavigableItems.with(
                unfilteredItems = directory.entries(),
                filter = if (directory == this.directory) filter else ""
            )
        )

    fun navigatedTo(path: Path?): State {
        if (path == null) return this
        if (directory == path) return this
        if (!path.isDirectory) return this

        tailrec fun Path.nearestChildToOrNull(parent: Path): Path? {
            if (this.parent == parent) return this
            return this.parent?.nearestChildToOrNull(parent)
        }

        return withDirectory(path).run {
            val nearestChild = directory.nearestChildToOrNull(parent = path)
            if (nearestChild != null) {
                // navigating to a parent directory, try to stay on the same entry
                withCursorOnFirst { it.path.name == nearestChild.name }
            } else {
                // navigating to an unrelated directory, go to the top
                withCursor(0)
            }
        }
    }

    fun navigatedUp() = navigatedTo(directory.parent)

    fun updatedEntries(preferredEntry: (Entry) -> Boolean = { it.path.name == currentItem?.path?.name }): State {
        return withDirectory(directory).withCursorOnFirst(predicate = preferredEntry)
    }

    private fun withFilterableItems(filterableNavigableItems: FilterableNavigableItemListState<Entry>) = copy(
        filterableNavigableItems = filterableNavigableItems,
        navigableItems = navigableItems.withItems(filterableNavigableItems.filteredItems)
    )

    override val unfilteredItems get() = filterableNavigableItems.unfilteredItems
    override val filter get() = filterableNavigableItems.filter
    override val filteredItems get() = filterableNavigableItems.filteredItems

    override fun withFilter(filter: String) = withFilterableItems(
        filterableNavigableItems.withFilter(filter)
    )

    private fun withNavigableItems(navigableItems: NavigableItemListState<Entry>) = copy(navigableItems = navigableItems)

    override val cursor get() = navigableItems.cursor
    override val currentItem get() = navigableItems.currentItem

    override fun withCursor(cursor: Int) = withNavigableItems(
        navigableItems.withCursor(cursor)
    )

    override fun withCursorShifted(offset: Int) = withNavigableItems(
        navigableItems.withCursorShifted(offset)
    )

    override fun withCursorOnFirst(default: Int, predicate: (Entry) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnFirst(default, predicate)
    )

    override fun withCursorOnNext(predicate: (Entry) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnNext(predicate)
    )

    override fun withCursorOnNextReverse(predicate: (Entry) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnNextReverse(predicate)
    )

    val shownMenuActions: List<MenuAction<State, MainController>> by lazy {
        menuActions.all.filter { it.isShown(inputMode) }
    }
    val menuCursor get() = rawMenuCursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    val isMenuOpen: Boolean get() = menuCursor >= 0
    val currentMenuAction: MenuAction<State, MainController>? by lazy {
        shownMenuActions.getOrNull(menuCursor)
    }

    fun withMenuCursor(cursor: Int) = copy(
        rawMenuCursor = cursor.coerceAtMost(shownMenuActions.lastIndex).coerceAtLeast(-1)
    )

    val isTypingCommand: Boolean get() = command != null

    fun withCommand(command: String?) = copy(command = command)

    fun inQuickMacroMode(enabled: Boolean = true) = copy(inQuickMacroMode = enabled)

    fun withInputMode(inputMode: InputMode?) = copy(inputMode = inputMode)

    fun withDialog(dialog: Widget?) = copy(dialog = dialog)

    fun withLastReceivedEvent(event: KeyboardEvent?) = copy(lastReceivedEvent = event)

    companion object {
        fun initial(
            startingDirectory: Path,
            showHiddenEntries: Boolean,
            normalModeActions: NormalModeActions,
            quickMacroModeActions: QuickMacroModeActions,
            menuActions: MenuActions,
        ): State {
            val filterableNavigableItems = FilterableNavigableItemListState.initial(
                unfilteredItems = startingDirectory.entries(),
                filter = "",
                filterOn = { path.name },
                hiddenOn = if (!showHiddenEntries) {
                    { isHidden }
                } else {
                    null
                }
            )
            return State(
                directory = startingDirectory,
                filterableNavigableItems = filterableNavigableItems,
                navigableItems = NavigableItemListState.initial(
                    items = filterableNavigableItems.filteredItems,
                    cursor = 0,
                    filterOn = { path.name }
                ),
                rawMenuCursor = -1,
                command = null,
                inQuickMacroMode = false,
                normalModeActions = normalModeActions,
                quickMacroModeActions = quickMacroModeActions,
                menuActions = menuActions,
                inputMode = null,
                dialog = null,
                lastReceivedEvent = null
            )
        }
    }
}

private fun Path.entries(): List<Entry> = children()
    .asSequence()
    .map { it.cleaned() } // fix broken paths
    .map { it.entry() }
    .sortedBy { it.path.name }
    .sortedByDescending { it.type == Directory }
    .toList()
