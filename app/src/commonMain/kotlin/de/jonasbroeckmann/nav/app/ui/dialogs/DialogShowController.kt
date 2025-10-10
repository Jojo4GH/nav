package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.ui.Decorator

interface DialogShowController {
    fun <R> showDialog(block: DialogRenderingScope.() -> R): R
}

interface DialogRenderingScope {
    fun render(widget: Widget)
}

inline fun <R> DialogRenderingScope.decorate(
    decorator: Decorator,
    block: DialogRenderingScope.() -> R
) = object : DialogRenderingScope {
    override fun render(widget: Widget) = this@decorate.render(decorator(widget))
}.block()
