package de.jonasbroeckmann.nav.framework.ui.dialog

data class DialogOptions(
    val hideMainTable: Boolean = false,
    val hidePath: Boolean = false,
) {
    companion object {
        val FullScreen = DialogOptions(hideMainTable = true, hidePath = false)
        val BorderlessFullScreen = DialogOptions(hideMainTable = true, hidePath = true)
    }
}
