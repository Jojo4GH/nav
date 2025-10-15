package de.jonasbroeckmann.nav.framework.semantics

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
