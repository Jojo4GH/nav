package de.jonasbroeckmann.nav.framework.semantics

class FilterableItemListSemantics<Self, Item>(
    private val self: Self,
    lazyItems: () -> List<Item>,
    override val filter: String,
    private val newWithFilter: Self.(String) -> Self,
    private val filterOn: Item.() -> String,
    private val preSort: List<Pair<Item, Double>>.() -> List<Pair<Item, Double>> = { this },
    private val onNoFilter: List<Item>.() -> List<Item> = { this }
) : FilterableItemList<Self, Item> where Self : FilterableItemList<Self, Item>, Self : NavigableItemList<Self, Item> {
    override val unfilteredItems: List<Item> by lazy(lazyItems)

    override val filteredItems: List<Item> by lazy {
        when {
            filter.isNotEmpty() -> {
                val lowercaseFilter = filter.lowercase()
                unfilteredItems
                    .mapNotNull { item ->
                        item.filterOn().scoreFor(lowercaseFilter)
                            .takeIf { it > 0.0 }
                            ?.let { item to it }
                    }
                    .preSort()
                    .sortedByDescending { (_, score) -> score }
                    .map { (item, _) -> item }
            }
            else -> unfilteredItems.onNoFilter()
        }
    }

    override fun withFilter(filter: String): Self {
        if (filter == this.filter) return self
        val tmp = self.newWithFilter(filter)
        return if (filteredItems.size > tmp.filteredItems.size) {
            // if we filtered something out, move the cursor to the best match
            tmp.withCursor(0)
        } else {
            // otherwise try to stay on the same entry
            tmp.withCursorOnFirst { it.filterOn() == self.currentItem?.filterOn() }
        }
    }
}

fun String.scoreFor(query: String): Double {
    if (length < query.length) return 0.0
    fun lengthRatio(): Double = query.length.toDouble() / length.toDouble()
    val indexWithoutCase = indexOf(query, ignoreCase = true)
    if (indexWithoutCase == 0) {
        return 2.0 + lengthRatio()
    }
    if (indexWithoutCase >= 0) {
        return 0.0 + lengthRatio()
    }
    return 0.0
}
