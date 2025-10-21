package de.jonasbroeckmann.nav.framework.semantics

data class FilterableItemListState<Item> private constructor(
    override val unfilteredItems: List<Item>,
    override val filter: String,
    private val filterOn: Item.() -> String,
    private val hiddenOn: (Item.() -> Boolean?)?,
    override val filteredItems: List<Item>
) : FilterableItemList<FilterableItemListState<Item>, Item> {

    fun with(unfilteredItems: List<Item>, filter: String) = copy(
        unfilteredItems = unfilteredItems,
        filter = filter,
        filteredItems = computeFiltered(
            items = unfilteredItems,
            filter = filter,
            filterOn = filterOn,
            hiddenOn = hiddenOn
        )
    )

    override fun withFilter(filter: String): FilterableItemListState<Item> {
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
        ): FilterableItemListState<Item> {
            return FilterableItemListState(
                unfilteredItems = unfilteredItems,
                filter = filter,
                filterOn = filterOn,
                hiddenOn = hiddenOn,
                filteredItems = computeFiltered(
                    items = unfilteredItems,
                    filter = filter,
                    filterOn = filterOn,
                    hiddenOn = hiddenOn
                )
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

        private fun String.scoreFor(query: String): Double {
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
    }
}
