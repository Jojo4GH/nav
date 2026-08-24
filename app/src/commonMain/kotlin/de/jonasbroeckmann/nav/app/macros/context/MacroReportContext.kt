package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext

interface MacroReportContext {
    context(traceContext: MacroTraceContext)
    fun reportDebug(includeTrace: Boolean = true, message: () -> String)

    context(traceContext: MacroTraceContext)
    fun reportWarning(includeTrace: Boolean = true, message: () -> String)
}
