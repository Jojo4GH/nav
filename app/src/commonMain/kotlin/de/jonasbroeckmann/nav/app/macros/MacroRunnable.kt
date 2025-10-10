package de.jonasbroeckmann.nav.app.macros

fun interface MacroRunnable {
    context(context: MacroRuntimeContext)
    fun run()
}
