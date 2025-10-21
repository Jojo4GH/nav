package de.jonasbroeckmann.nav.framework.semantics

import de.jonasbroeckmann.nav.framework.utils.commonPrefix

enum class AutocompleteStyle {
    CommonPrefixStop,
    CommonPrefixCycle,
}

enum class AutocompleteAutoNavigation {
    None,
    OnSingle,
    OnSingleAfterCompletion,
}

fun <T, Item> T.autocomplete(
    autocompleteOn: Item.() -> String,
    style: AutocompleteStyle,
    autoNavigation: AutocompleteAutoNavigation,
    invertDirection: Boolean
): AutocompleteAction<T, Item> where T : FilterableItemList<T, Item>, T : NavigableItemList<T, Item> {
    val commonPrefix = unfilteredItems
        .map { it.autocompleteOn().lowercase() }
        .filter { it.startsWith(filter.lowercase()) }
        .ifEmpty { return AutocompleteAction(this) }
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
        return AutocompleteAction(completedState)
    }
    completedState.filteredItems
        .singleOrNull { it.autocompleteOn().startsWith(commonPrefix, ignoreCase = true) }
        ?.let { singleEntry ->
            if (autoNavigation == OnSingleAfterCompletion) {
                if (!hasFilterChanged) {
                    return AutocompleteAction(completedState, singleEntry)
                }
            }
            if (autoNavigation == OnSingle) {
                return AutocompleteAction(completedState, singleEntry)
            }
        }

    return AutocompleteAction(completedState)
}

data class AutocompleteAction<T, Item>(
    val newState: T,
    val autoNavigate: Item? = null
)
