package de.jonasbroeckmann.nav.framework.ui.dialog

interface DialogShowController {
    fun <R> showDialog(options: DialogOptions = DialogOptions(), block: DialogShowScope.() -> R): R
}
