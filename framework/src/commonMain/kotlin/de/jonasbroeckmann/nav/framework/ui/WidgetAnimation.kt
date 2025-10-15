package de.jonasbroeckmann.nav.framework.ui

import com.github.ajalt.mordant.animation.StoppableAnimation
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.terminal.Terminal

class WidgetAnimation(terminal: Terminal) : StoppableAnimation {
    private val animation = terminal.animation<Widget> { it }

    fun render(widget: Widget) = animation.update(widget)

    override fun clear() = animation.clear()

    override fun stop() = animation.stop()
}
