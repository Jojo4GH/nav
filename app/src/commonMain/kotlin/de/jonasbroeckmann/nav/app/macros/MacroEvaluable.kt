package de.jonasbroeckmann.nav.app.macros

fun interface MacroEvaluable<R> {
    context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
    fun evaluate(): R
}
