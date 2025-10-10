package de.jonasbroeckmann.nav.app.state.semantics

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
