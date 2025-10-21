package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.TextAlign.LEFT
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.ui.highlightFilterOccurrences
import de.jonasbroeckmann.nav.app.ui.render
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.config
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.framework.action.DialogKeyAction
import de.jonasbroeckmann.nav.framework.action.KeyAction
import de.jonasbroeckmann.nav.framework.action.buildDialogKeyActions
import de.jonasbroeckmann.nav.framework.action.handle
import de.jonasbroeckmann.nav.framework.action.register
import de.jonasbroeckmann.nav.framework.semantics.*
import de.jonasbroeckmann.nav.framework.ui.appendTextFieldContent
import de.jonasbroeckmann.nav.framework.ui.buildHints
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.dismissDialog
import de.jonasbroeckmann.nav.framework.ui.dialog.updateState

context(context: FullContext)
fun DialogShowScope.defaultChoicePrompt(
    title: String,
    choices: List<String>,
    defaultChoice: Int = 0,
): String? = choicePrompt(
    title = title,
    choices = choices,
    defaultChoice = defaultChoice,
    showHints = !config.hideHints,
    accessibilityDecorations = context.accessibilityDecorations,
    keys = config.keys,
    autocomplete = config.autocomplete
)

@Suppress("detekt:LongMethod", "detekt:CyclomaticComplexMethod")
context(stylesProvider: StylesProvider)
fun DialogShowScope.choicePrompt(
    title: String,
    choices: List<String>,
    defaultChoice: Int = 0,
    showHints: Boolean,
    accessibilityDecorations: Boolean,
    keys: Config.Keys,
    autocomplete: Config.Autocomplete
): String? {
    val inputFilterAction: DialogKeyAction<ChoicePromptState, String?>
    val actions = buildDialogKeyActions<ChoicePromptState, String?> {
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
            action = { input ->
                updateState {
                    val action = autocomplete(
                        autocompleteOn = { this },
                        style = autocomplete.style.value,
                        autoNavigation = autocomplete.autoNavigation.value,
                        invertDirection = input.shift
                    )
                    action.autoNavigate?.let { item -> dismissDialog(item) }
                    action.newState
                }
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
            action = { updateState { withCursor(0) } }
        )
        register(
            keys.cursor.end,
            hidden = { true },
            condition = { filteredItems.isNotEmpty() },
            action = { updateState { withCursor(filteredItems.lastIndex) } }
        )
        inputFilterAction = register(
            KeyAction(
                keys = null,
                description = { "type to filter" },
                condition = { true },
                action = { input ->
                    input.updateTextField(
                        current = filter,
                        onChange = { newFilter -> updateState { withFilter(newFilter) } }
                    )
                }
            )
        )
    }
    return inputDialog(
        initialState = ChoicePromptState.initial(
            items = choices,
            cursor = defaultChoice
        ),
        onInput = { input -> actions.handle(state, input, inputMode) }
    ) {
        verticalLayout {
            align = LEFT
            cell(title)
            if (filter.isNotEmpty()) {
                val filterString = buildString {
                    append("❯ ")
                    appendTextFieldContent(
                        text = filter,
                        hasFocus = inputFilterAction.isAvailable(inputMode)
                    )
                }
                cell(styles.filter(filterString))
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
                    buildHints(styles.genericElements(" • ")) {
                        addActions(actions, this@inputDialog, inputMode) { render() }
                    }.let {
                        "${styles.genericElements("•")} $it"
                    }
                )
            }
        }
    }
}

private data class ChoicePromptState private constructor(
    private val filterableItems: FilterableItemListState<String>,
    private val navigableItems: NavigableItemListState<String>
) : FilterableItemList<ChoicePromptState, String>, NavigableItemList<ChoicePromptState, String> {
    private fun withFilterableItems(filterableItems: FilterableItemListState<String>) = copy(
        filterableItems = filterableItems,
        navigableItems = navigableItems.withItems(filterableItems.filteredItems)
    )

    override val unfilteredItems get() = filterableItems.unfilteredItems
    override val filter get() = filterableItems.filter
    override val filteredItems get() = filterableItems.filteredItems

    override fun withFilter(filter: String) = withFilterableItems(
        filterableItems.withFilter(filter)
    )

    private fun withNavigableItems(navigableItems: NavigableItemListState<String>) = copy(
        navigableItems = navigableItems
    )

    override val cursor get() = navigableItems.cursor
    override val currentItem get() = navigableItems.currentItem

    override fun withCursor(cursor: Int) = withNavigableItems(
        navigableItems.withCursor(cursor)
    )

    override fun withCursorShifted(offset: Int) = withNavigableItems(
        navigableItems.withCursorShifted(offset)
    )

    override fun withCursorOnFirst(default: Int, predicate: (String) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnFirst(default, predicate)
    )

    override fun withCursorOnNext(predicate: (String) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnNext(predicate)
    )

    override fun withCursorOnNextReverse(predicate: (String) -> Boolean) = withNavigableItems(
        navigableItems.withCursorOnNextReverse(predicate)
    )

    companion object {
        fun initial(
            items: List<String>,
            cursor: Int
        ): ChoicePromptState {
            val filterableItems = FilterableItemListState.initial(
                unfilteredItems = items,
                filter = "",
                filterOn = { this },
                hiddenOn = null
            )
            return ChoicePromptState(
                filterableItems = filterableItems,
                navigableItems = NavigableItemListState.initial(
                    items = filterableItems.filteredItems,
                    cursor = cursor,
                    filterOn = { this }
                )
            )
        }
    }
}
