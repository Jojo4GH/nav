package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.animation.StoppableAnimation
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.properties.Delegates

class RebuildableAnimation(
    terminal: Terminal,
    private val onBuild: () -> Widget
) : StoppableAnimation {
    private val animation = terminal.animation<Widget> { it }

    private var currentWidget: Widget? = null

    fun invalidate() {
        currentWidget = null
    }

    fun update(force: Boolean = false) {
        var widget = currentWidget
        if (widget == null || force) {
            widget = onBuild()
            currentWidget = widget
        }
        animation.update(widget)
    }

    override fun clear() {
        animation.clear()
    }

    override fun stop() {
        animation.stop()
    }
}

fun <T> RebuildableAnimation.invalidating(initial: T) = Delegates.observable(initial) { _, old, new ->
    if (old != new) {
        invalidate()
    }
}
