package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.InputMode
import de.jonasbroeckmann.nav.app.ui.Decorator

interface DialogShowController {
    fun <R> showDialog(block: DialogScope.() -> R): R
}

interface DialogScope : InputMode {
    fun render(widget: Widget)
}

inline fun <R> DialogScope.decorate(
    decorator: Decorator,
    block: DialogScope.() -> R
) = object : DialogScope, InputMode by this {
    override fun render(widget: Widget) = this@decorate.render(decorator(widget))
}.block()
