package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.TextAlign.LEFT
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.buildKeyActions
import de.jonasbroeckmann.nav.app.actions.handle
import de.jonasbroeckmann.nav.app.actions.register
import de.jonasbroeckmann.nav.app.state.semantics.*
import de.jonasbroeckmann.nav.app.ui.buildHints
import de.jonasbroeckmann.nav.app.ui.highlightFilterOccurrences
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.config
import de.jonasbroeckmann.nav.config.styles

context(context: FullContext)
fun DialogScope.defaultChoicePrompt(
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
fun DialogScope.choicePrompt(
    title: String,
    choices: List<String>,
    defaultChoice: Int = 0,
    showHints: Boolean,
    accessibilityDecorations: Boolean,
    keys: Config.Keys,
    autocomplete: Config.Autocomplete
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
                    onAutoNavigate = { _, item -> dismissDialog(item) }
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
        }
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

    override fun withCursorOnFirst(
        default: Int,
        predicate: (String) -> Boolean
    ) = navigableItemListSemantics.withCursorOnFirst(default, predicate)

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
