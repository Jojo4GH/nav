package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.rendering.WidthRange
import com.github.ajalt.mordant.terminal.Terminal

class FillLayout(
    val top: () -> Widget,
    val fill: (availableLines: Int?) -> Widget,
    val bottom: () -> Widget,
    val limitToTerminalHeight: Boolean
) : Widget {
    override fun measure(t: Terminal, width: Int) = WidthRange(min = width, max = width)

    override fun render(t: Terminal, width: Int): Lines {
        val topRendered = top().render(t)
        val bottomRendered = bottom().render(t)

        val availableLines = if (limitToTerminalHeight) {
            t.updateSize()
            t.size.height - topRendered.height - bottomRendered.height
        } else {
            null
        }

        val fillRendered = fill(availableLines).render(t, width)

        return Lines(topRendered.lines + fillRendered.lines + bottomRendered.lines)
    }
}
