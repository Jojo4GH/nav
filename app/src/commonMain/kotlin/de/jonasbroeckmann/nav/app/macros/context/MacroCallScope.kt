package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope

interface MacroCallScope : MacroRunContext, MacroStorageScope, MacroCaller, MacroReportContext {
    val rootContext: MacroRunContext
    val parentCall: MacroCallScope?
    val currentMacro: Macro
    fun doReturn(): Nothing

    context(_: MacroTraceContext)
    fun <R> showMacroDialog(options: DialogOptions = DialogOptions(), block: DialogShowScope.() -> R): R
}