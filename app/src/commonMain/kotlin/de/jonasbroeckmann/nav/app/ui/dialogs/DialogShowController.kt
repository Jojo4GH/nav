package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.InputModeScope
import de.jonasbroeckmann.nav.app.ui.Decorator

interface DialogShowController {
    fun <R> showDialog(block: DialogShowScope.() -> R): R
}

interface DialogShowScope : InputModeScope {
    fun render(widget: Widget)
}

inline fun <R> DialogShowScope.decorate(
    decorator: Decorator,
    block: DialogShowScope.() -> R
) = object : DialogShowScope, InputModeScope by this {
    override fun render(widget: Widget) = this@decorate.render(decorator(widget))
}.block()
