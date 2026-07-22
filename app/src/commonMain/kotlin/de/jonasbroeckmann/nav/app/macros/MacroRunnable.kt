package de.jonasbroeckmann.nav.app.macros

sealed interface MacroRunnable {
    context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
    fun run()
}
