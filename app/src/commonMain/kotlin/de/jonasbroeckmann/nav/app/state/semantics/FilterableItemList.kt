package de.jonasbroeckmann.nav.app.state.semantics

interface FilterableItemList<Self : FilterableItemList<Self, Item>, Item> {
    val unfilteredItems: List<Item>
    val filter: String
    val filteredItems: List<Item>
    fun withFilter(filter: String): Self
}
