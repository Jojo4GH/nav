package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.animation.StoppableAnimation
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.properties.Delegates

interface Invalidatable {
    fun invalidate()
}

class WidgetRebuilder(private val onBuild: () -> Widget) : Invalidatable {
    private var currentWidget: Widget? = null

    override fun invalidate() {
        currentWidget = null
    }

    val widget: Widget get() = currentWidget ?: onBuild().also { currentWidget = it }
}

class RebuildableAnimation(
    terminal: Terminal,
    private val rebuilder: WidgetRebuilder
) : StoppableAnimation, Invalidatable by rebuilder {

    constructor(terminal: Terminal, onBuild: () -> Widget) : this(terminal, WidgetRebuilder(onBuild))

    private val animation = terminal.animation<Widget> { it }

    fun update() {
        animation.update(rebuilder.widget)
    }

    override fun clear() {
        animation.clear()
    }

    override fun stop() {
        animation.stop()
    }
}

fun <T> Invalidatable.invalidating(initial: T) = Delegates.observable(initial) { _, old, new ->
    if (old != new) {
        invalidate()
    }
}
