package de.jonasbroeckmann.nav.framework.ui.dialog

interface DialogController<T, in R> {
    val state: T

    fun updateState(update: T.() -> T)

    fun dismissDialog(value: R): Nothing
}

context(controller: DialogController<T, R>)
fun <T, R> updateState(update: T.() -> T) = controller.updateState(update)

context(controller: DialogController<T, R>)
fun <T, R> dismissDialog(value: R): Nothing = controller.dismissDialog(value)
