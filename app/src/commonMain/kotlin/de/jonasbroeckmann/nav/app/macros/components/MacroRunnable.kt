package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.context.MacroCallScope

sealed interface MacroRunnable {
    context(scope: MacroCallScope, traceContext: MacroTraceContext)
    fun run()

    typealias Action = context(MacroCallScope, MacroTraceContext) () -> Unit
}
