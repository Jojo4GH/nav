package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.TextAlign.LEFT
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.buildKeyActions
import de.jonasbroeckmann.nav.app.actions.handle
import de.jonasbroeckmann.nav.app.actions.register
import de.jonasbroeckmann.nav.app.ui.buildHints
import de.jonasbroeckmann.nav.app.ui.highlightFilterOccurrences
import de.jonasbroeckmann.nav.app.updateTextField
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.Config.Autocomplete.Style.CommonPrefixCycle
import de.jonasbroeckmann.nav.config.Config.Autocomplete.Style.CommonPrefixStop
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.utils.commonPrefix
import kotlin.collections.ifEmpty
import kotlin.text.startsWith
import kotlin.time.Duration


interface NavigableItemList<Self : NavigableItemList<Self, Item>, Item> {
    val cursor: Int
    val currentItem: Item?
    fun withCursorCoerced(cursor: Int): Self
    fun withCursorShifted(offset: Int): Self
    fun withCursorOnFirst(default: Int = cursor, predicate: (Item) -> Boolean): Self
    fun withCursorOnNext(predicate: (Item) -> Boolean): Self
    fun withCursorOnNextReverse(predicate: (Item) -> Boolean): Self
}

class NavigableItemListSemantics<Self : NavigableItemList<Self, Item>, Item>(
    private val self: Self,
    lazyItems: () -> List<Item>,
    override val cursor: Int,
    private val copyWithCursor: Self.(Int) -> Self
) : NavigableItemList<Self, Item> {
    private val items by lazy(lazyItems)

    override val currentItem get() = items.getOrNull(cursor)

    override fun withCursorCoerced(cursor: Int): Self {
        val cursorCoerced = cursor.coerceAtMost(items.lastIndex).coerceAtLeast(0)
        return if (cursorCoerced != this.cursor) self.copyWithCursor(cursorCoerced) else self
    }

    override fun withCursorShifted(offset: Int) = withCursorCoerced(
        cursor = when {
            items.isEmpty() -> 0
            else -> (cursor + offset).mod(items.size)
        }
    )

    override fun withCursorOnFirst(default: Int, predicate: (Item) -> Boolean) = withCursorCoerced(
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
    ): Self {
        for (offset in offsets) {
            val i = (cursor + offset).mod(items.size)
            if (predicate(items[i])) {
                return withCursorCoerced(i)
            }
        }
        return self
    }
}

interface FilterableItemList<Self : FilterableItemList<Self, Item>, Item> {
    val unfilteredItems: List<Item>
    val filter: String
    val filteredItems: List<Item>
    fun withFilter(filter: String): Self
}

class FilterableItemListSemantics<Self, Item>(
    private val self: Self,
    lazyItems: () -> List<Item>,
    override val filter: String,
    private val copyWithFilter: Self.(String) -> Self,
    private val filterOn: Item.() -> String,
    private val preSort: List<Item>.() -> List<Item> = { this },
    private val onNoFilter: List<Item>.() -> List<Item> = { this }
) : FilterableItemList<Self, Item> where Self : FilterableItemList<Self, Item>, Self : NavigableItemList<Self, Item> {
    override val unfilteredItems: List<Item> by lazy(lazyItems)

    override val filteredItems: List<Item> by lazy {
        when {
            filter.isNotEmpty() -> {
                val lowercaseFilter = filter.lowercase()
                unfilteredItems
                    .filter { lowercaseFilter in it.filterOn().lowercase() }
                    .preSort()
                    .sortedByDescending { it.filterOn().startsWith(filter, ignoreCase = true) }
                    .sortedByDescending { it.filterOn().startsWith(filter) }
            }
            else -> unfilteredItems.onNoFilter()
        }
    }

    override fun withFilter(filter: String): Self {
        if (filter == this.filter) return self
        val tmp = self.copyWithFilter(filter)
        return if (filteredItems.size > tmp.filteredItems.size) {
            // if we filtered something out, move the cursor to the best match
            tmp.withCursorCoerced(0)
        } else {
            // otherwise try to stay on the same entry
            tmp.withCursorOnFirst { it.filterOn() == self.currentItem?.filterOn() }
        }
    }
}

fun <T, Item> T.autocomplete(
    autocompleteOn: Item.() -> String,
    style: Config.Autocomplete.Style,
    autoNavigation: Config.Autocomplete.AutoNavigation,
    invertDirection: Boolean,
    onUpdate: (T) -> Unit,
    onAutoNavigate: T.(Item) -> Unit,
) where T : FilterableItemList<T, Item>, T : NavigableItemList<T, Item> {
    val commonPrefix = unfilteredItems
        .map { it.autocompleteOn().lowercase() }
        .filter { it.startsWith(filter.lowercase()) }
        .ifEmpty { return }
        .commonPrefix()

    val filteredState = withFilter(commonPrefix)
    val hasFilterChanged = !filteredState.filter.equals(filter, ignoreCase = true)

    // Handle autocomplete
    val completedState = when (style) {
        CommonPrefixStop -> {
            filteredState.withCursorOnFirst { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
        }
        CommonPrefixCycle -> {
            if (hasFilterChanged) {
                // Go to first
                filteredState.withCursorOnFirst { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
            } else {
                if (invertDirection) {
                    // Go to previous
                    filteredState.withCursorOnNextReverse { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
                } else {
                    // Go to next
                    filteredState.withCursorOnNext { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
                }
            }
        }
    }

    // Handle auto-navigation
    if (autoNavigation == None) {
        onUpdate(completedState)
        return
    }
    completedState.filteredItems
        .singleOrNull { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
        ?.let { singleEntry ->
            if (autoNavigation == OnSingleAfterCompletion) {
                if (!hasFilterChanged) {
                    completedState.onAutoNavigate(singleEntry)
                    return
                }
            }
            if (autoNavigation == OnSingle) {
                completedState.onAutoNavigate(singleEntry)
                return
            }
        }

    onUpdate(completedState)
}


private data class ChoicePromptState private constructor(
    override val unfilteredItems: List<String>,
    override val filter: String,
    override val cursor: Int,
) : FilterableItemList<ChoicePromptState, String>, NavigableItemList<ChoicePromptState, String> {
    private val filterableItemListSemantics = FilterableItemListSemantics(
        self = this,
        lazyItems = { unfilteredItems },
        filter = filter,
        copyWithFilter = { copy(filter = it) },
        filterOn = { this }
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

    override fun withCursorOnFirst(default: Int, predicate: (String) -> Boolean) = navigableItemListSemantics.withCursorOnFirst(default, predicate)

    override fun withCursorOnNext(predicate: (String) -> Boolean) = navigableItemListSemantics.withCursorOnNext(predicate)

    override fun withCursorOnNextReverse(predicate: (String) -> Boolean) = navigableItemListSemantics.withCursorOnNextReverse(predicate)

    companion object {
        fun initial(
            items: List<String>,
            cursor: Int
        ) = ChoicePromptState(
            unfilteredItems = items,
            filter = "",
            cursor = cursor.coerceIn(items.indices)
        )
    }
}

context(context: FullContext)
fun DialogRenderingScope.defaultChoicePrompt(
    title: String,
    choices: List<String>,
    defaultChoice: Int = 0,
): String? = choicePrompt(
    title = title,
    choices = choices,
    defaultChoice = defaultChoice,
    showHints = !context.config.hideHints,
    accessibilityDecorations = context.accessibilityDecorations,
    keys = context.config.keys,
    autocomplete = context.config.autocomplete,
    inputTimeout = context.inputTimeout
)

context(context: PartialContext, stylesProvider: StylesProvider)
fun DialogRenderingScope.choicePrompt(
    title: String,
    choices: List<String>,
    defaultChoice: Int = 0,
    showHints: Boolean,
    accessibilityDecorations: Boolean,
    keys: Config.Keys,
    autocomplete: Config.Autocomplete,
    inputTimeout: Duration
): String? {
    val actions: List<DialogKeyAction<ChoicePromptState, String?>> = buildKeyActions {
        register(
            keys.cursor.up, keys.menu.up,
            condition = { filteredItems.isNotEmpty() },
            action = { updateState { withCursorShifted(-1) } }
        )
        register(
            keys.cursor.down, keys.menu.down,
            condition = { filteredItems.isNotEmpty() },
            action = { updateState { withCursorShifted(+1) } }
        )
        register(
            keys.filter.autocomplete, keys.filter.autocomplete.copy(shift = true),
            description = { "autocomplete" },
            condition = { unfilteredItems.isNotEmpty() },
            action = {
                autocomplete(
                    autocompleteOn = { this },
                    style = autocomplete.style,
                    autoNavigation = autocomplete.autoNavigation,
                    invertDirection = it.shift,
                    onUpdate = { newState -> updateState { newState } },
                    onAutoNavigate = { item -> dismissDialog(item) }
                )
            }
        )
        register(
            keys.filter.clear,
            description = { "clear filter" },
            condition = { filter.isNotEmpty() },
            action = { updateState { withFilter("") } }
        )
        register(
            keys.cancel,
            description = { "cancel" },
            condition = { true },
            action = { dismissDialog(null) }
        )
        register(
            keys.submit,
            description = { "submit" },
            condition = { currentItem != null },
            action = { dismissDialog(currentItem) }
        )
        register(
            keys.cursor.home,
            hidden = { true },
            condition = { filteredItems.isNotEmpty() },
            action = { updateState { withCursorCoerced(0) } }
        )
        register(
            keys.cursor.end,
            hidden = { true },
            condition = { filteredItems.isNotEmpty() },
            action = { updateState { withCursorCoerced(filteredItems.lastIndex) } }
        )
    }
    return inputDialog(
        initialState = ChoicePromptState.initial(
            items = choices,
            cursor = defaultChoice
        ),
        onInput = onInput@{ input ->
            if (actions.handle(state, input)) return@onInput
            input.updateTextField(state.filter) { newFilter ->
                updateState { withFilter(newFilter) }
            }
        },
        inputTimeout = inputTimeout,
    ) {
        verticalLayout {
            align = LEFT
            cell(title)
            if (filter.isNotEmpty()) {
                cell(styles.filter("❯ $filter"))
            }
            if (filteredItems.isEmpty()) {
                cell(styles.genericElements("nothing"))
            } else {
                val filterMarkerStyle = styles.filterMarker + TextStyles.bold
                val unselectedNamePrefix = " "
                val selectedNamePrefix = if (accessibilityDecorations) "▊" else " "
                for ((i, choice) in filteredItems.withIndex()) {
                    val isSelected = i == cursor
                    val decorated = choice
                        .let { highlightFilterOccurrences(it, filter, filterMarkerStyle) }
                        .let { if (isSelected) TextStyles.inverse(it) else it }
                        .let { "\u0006$it" } // prevent filter highlighting from getting removed
                        .let {
                            when (isSelected) {
                                true -> "${styles.genericElements("│")}${selectedNamePrefix}$it "
                                false -> "${styles.genericElements("│")}${unselectedNamePrefix}$it "
                            }
                        }
                    cell(decorated)
                }
            }
            if (showHints) {
                cell(
                    buildHints {
                        addActions(actions, this@inputDialog)
                    }.let {
                        "${styles.genericElements("•")} $it"
                    }
                )
            }
        }
    }
}
