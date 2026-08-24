package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope

fun interface MacroEvaluable<out R> {
    context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
    fun evaluate(): R
}
