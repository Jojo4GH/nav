package de.jonasbroeckmann.nav.framework.semantics

data class NavigableItemListState<Item> private constructor(
    private val items: List<Item>,
    override val cursor: Int,
    private val filterOn: Item.() -> String,
) : NavigableItemList<NavigableItemListState<Item>, Item> {
    override val currentItem by lazy { items.getOrNull(cursor) }

    fun withItems(items: List<Item>): NavigableItemListState<Item> {
        return if (items.size < this.items.size) {
            // if we filtered something out, move the cursor to the best match
            copy(
                items = items,
                cursor = 0
            )
        } else {
            // otherwise try to stay on the same entry
            copy(
                items = items,
                cursor = items
                    .indexOfFirst { it.filterOn() == currentItem?.filterOn() }
                    .takeIf { it >= 0 }
                    ?: cursor
            )
        }
    }

    override fun withCursor(cursor: Int): NavigableItemListState<Item> {
        val cursorCoerced = cursor.coerceAtMost(items.lastIndex).coerceAtLeast(0)
        return if (cursorCoerced != this.cursor) copy(cursor = cursorCoerced) else this
    }

    override fun withCursorShifted(offset: Int) = withCursor(
        cursor = when {
            items.isEmpty() -> 0
            else -> (cursor + offset).mod(items.size)
        }
    )

    override fun withCursorOnFirst(default: Int, predicate: (Item) -> Boolean) = withCursor(
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
    ): NavigableItemListState<Item> {
        for (offset in offsets) {
            val i = (cursor + offset).mod(items.size)
            if (predicate(items[i])) {
                return withCursor(i)
            }
        }
        return withCursor(cursor)
    }

    companion object {
        fun <Item> initial(
            items: List<Item>,
            cursor: Int,
            filterOn: Item.() -> String
        ) = NavigableItemListState(
            items = items,
            cursor = cursor.coerceAtMost(items.lastIndex).coerceAtLeast(0),
            filterOn = filterOn
        )
    }
}
