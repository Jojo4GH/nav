package de.jonasbroeckmann.nav.framework.ui.dialog

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.framework.input.InputModeScope
import de.jonasbroeckmann.nav.framework.ui.Decorator

interface DialogShowScope : InputModeScope {
    fun render(widget: Widget)
}

inline fun <R> DialogShowScope.decorate(
    decorator: Decorator,
    block: DialogShowScope.() -> R
) = object : DialogShowScope, InputModeScope by this {
    override fun render(widget: Widget) = this@decorate.render(decorator(widget))
}.block()
