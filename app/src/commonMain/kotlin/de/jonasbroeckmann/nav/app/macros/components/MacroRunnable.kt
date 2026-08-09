package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.context.MacroRuntimeContext

sealed interface MacroRunnable {
    context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
    fun run()
}
