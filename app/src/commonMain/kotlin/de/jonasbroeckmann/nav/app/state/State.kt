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
import de.jonasbroeckmann.nav.framework.semantics.FilterableItemListState
import de.jonasbroeckmann.nav.framework.semantics.NavigableItemList
import de.jonasbroeckmann.nav.framework.semantics.NavigableItemListState
import de.jonasbroeckmann.nav.utils.children
import de.jonasbroeckmann.nav.utils.cleaned
import de.jonasbroeckmann.nav.utils.isDirectory
import kotlinx.io.files.Path

@Suppress("detekt:TooManyFunctions")
data class State private constructor(
    val directory: Path,

    private val filterableItems: FilterableItemListState<Entry>,
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
            filterableItems.with(
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

        return withDirectory(path).let { tmp ->
            val nearestChild = directory.nearestChildToOrNull(parent = path)
            if (nearestChild != null) {
                // navigating to a parent directory, try to stay on the same entry
                tmp.withCursorOnFirst { it.path.name == nearestChild.name }
            } else {
                // navigating to an unrelated directory, go to the top
                tmp.withCursor(0)
            }
        }
    }

    fun navigatedUp() = navigatedTo(directory.parent)

    fun updatedEntries(preferredEntry: (Entry) -> Boolean = { it.path.name == currentItem?.path?.name }): State {
        return withDirectory(directory).withCursorOnFirst(predicate = preferredEntry)
    }

    private fun withFilterableItems(filterableItems: FilterableItemListState<Entry>) = copy(
        filterableItems = filterableItems,
        navigableItems = navigableItems.withItems(filterableItems.filteredItems)
    )

    override val unfilteredItems get() = filterableItems.unfilteredItems
    override val filter get() = filterableItems.filter
    override val filteredItems get() = filterableItems.filteredItems

    override fun withFilter(filter: String) = withFilterableItems(
        filterableItems.withFilter(filter)
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
            val filterableItems = FilterableItemListState.initial(
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
                filterableItems = filterableItems,
                navigableItems = NavigableItemListState.initial(
                    items = filterableItems.filteredItems,
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
