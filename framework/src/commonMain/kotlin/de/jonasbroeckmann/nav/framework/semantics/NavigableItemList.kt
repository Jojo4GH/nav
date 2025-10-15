package de.jonasbroeckmann.nav.framework.semantics

interface NavigableItemList<Self : NavigableItemList<Self, Item>, Item> {
    val cursor: Int

    val currentItem: Item?

    fun withCursorCoerced(cursor: Int): Self

    fun withCursorShifted(offset: Int): Self

    fun withCursorOnFirst(default: Int = cursor, predicate: (Item) -> Boolean): Self

    fun withCursorOnNext(predicate: (Item) -> Boolean): Self

    fun withCursorOnNextReverse(predicate: (Item) -> Boolean): Self
}
