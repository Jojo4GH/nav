package de.jonasbroeckmann.nav.framework.ui.dialog

interface DialogShowController {
    fun <R> showDialog(block: DialogShowScope.() -> R): R
}
