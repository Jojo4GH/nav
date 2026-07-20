package de.jonasbroeckmann.nav.framework.ui

import com.github.ajalt.mordant.rendering.Line
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Span
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.VerticalAlign
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.terminal.Terminal

class FlowRowLayout(
    private val items: List<Widget>,
    private val itemSpacing: Int = 0,
    private val rowSpacing: Int = 0,
    private val horizontalAlignment: TextAlign = LEFT,
    private val verticalAlignment: VerticalAlign = TOP
) : Widget {
    init {
        require(itemSpacing >= 0) { "item spacing cannot be negative" }
        require(rowSpacing >= 0) { "row spacing cannot be negative" }
    }

    override fun measure(t: Terminal, width: Int): WidthRange {
        val measuredItems = items.map { it.measure(t, width) }
        return WidthRange(
            min = measuredItems.maxOf { it.min },
            max = measuredItems.sumOf { it.max } + (items.size - 1) * itemSpacing
        )
    }

    override fun render(t: Terminal, width: Int): Lines {
        val measuredItems = items.map { it to it.measure(t, width) }
        val lines = buildList {
            val remainingItems = measuredItems.toMutableList()
            while (remainingItems.isNotEmpty()) {
                if (isNotEmpty()) repeat(rowSpacing) { add(Line(emptyList())) }
                addAll(buildRow(remainingItems, t, width).lines)
            }
        }
        return Lines(lines)
    }

    private fun buildRow(remainingItems: MutableList<Pair<Widget, WidthRange>>, t: Terminal, width: Int): Lines {
        if (remainingItems.isEmpty()) return Lines(emptyList())

        val usedItems = mutableListOf<Pair<Widget, WidthRange>>()
        var usedWidth = 0
        while (remainingItems.isNotEmpty()) {
            val (_, itemWidthRange) = remainingItems.first()
            val itemMinWidth = itemWidthRange.min
            val appendWidth = if (usedItems.isEmpty()) itemMinWidth else itemSpacing + itemMinWidth
            if (usedWidth + appendWidth > width) break
            usedItems += remainingItems.removeFirst()
            usedWidth += appendWidth
        }
        if (usedItems.isEmpty()) {
            usedItems += remainingItems.removeFirst()
        }

        val renderedItems = usedItems.map { (item, itemWidthRange) -> item.render(t, itemWidthRange.min) }
        val renderedItemsWithSpacing = spaceItems(
            items = renderedItems,
            additionalSpacing = itemSpacing,
            unusedWidth = width - renderedItems.sumOf { it.width },
            horizontalAlignment = horizontalAlignment
        )
        return concatenateItems(
            items = renderedItemsWithSpacing,
            verticalAlignment = verticalAlignment
        )
    }
}

private fun spaceItems(items: List<Lines>, additionalSpacing: Int, unusedWidth: Int, horizontalAlignment: TextAlign): List<Lines> {
    val height = items.maxOf { it.height }

    fun spacer(width: Int): Lines {
        val line = Line(listOf(Span.space(width)))
        return Lines(List(height) { line })
    }

    fun Iterable<Lines>.insertInBetween(spacer: Lines) = buildList {
        this@insertInBetween.forEachIndexed { i, item ->
            if (i > 0) add(spacer)
            add(item)
        }
    }

    return when (horizontalAlignment) {
        LEFT -> items.insertInBetween(spacer(additionalSpacing)) + spacer(unusedWidth)
        RIGHT -> listOf(spacer(unusedWidth)) + items.insertInBetween(spacer(additionalSpacing))
        CENTER -> {
            val (spacer1, spacer2) = computeFairDivisions(unusedWidth, 2)
                .map { spacer(it) }
                .toList()
            listOf(spacer1) + items.insertInBetween(spacer(additionalSpacing)) + spacer2
        }
        JUSTIFY -> {
            val spacers = computeFairDivisions(unusedWidth, items.size - 1)
                .map { spacer(it + additionalSpacing) }
                .toList()
            buildList {
                spacers.forEachIndexed { i, spacer ->
                    add(items[i])
                    add(spacer)
                }
                add(items.last())
            }
        }
        NONE -> items
    }
}

private fun computeFairDivisions(available: Int, count: Int): Sequence<Int> {
    var available = available
    var count = count
    return sequence {
        while (count > 0) {
            val division = available / count
            yield(division)
            available -= division
            count--
        }
    }
}

private fun concatenateItems(items: List<Lines>, verticalAlignment: VerticalAlign): Lines {
    val height = items.maxOf { it.height }
    val lines = List(height) { mutableListOf<Span>() }
    items.forEach { itemLines ->
        val itemWidth = itemLines.width
        val topOffset = when (verticalAlignment) {
            TOP -> 0
            MIDDLE -> (height - itemLines.height) / 2
            BOTTOM -> height - itemLines.height
        }
        lines.forEachIndexed { y, spans ->
            val itemLine = itemLines.lines.getOrNull(y - topOffset)?.spans ?: listOf(Span.space(itemWidth))
            spans.addAll(itemLine)
        }
    }
    return Lines(lines.map { Line(it) })
}