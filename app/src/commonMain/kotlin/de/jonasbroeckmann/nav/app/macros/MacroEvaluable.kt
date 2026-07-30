package de.jonasbroeckmann.nav.app.macros

fun interface MacroEvaluable<out R> {
    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    fun evaluate(): R
}
