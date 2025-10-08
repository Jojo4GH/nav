package de.jonasbroeckmann.nav.app.macros

fun interface MacroEvaluable<R> {
    context(scope: MacroVariableScope)
    fun evaluate(): R
}
