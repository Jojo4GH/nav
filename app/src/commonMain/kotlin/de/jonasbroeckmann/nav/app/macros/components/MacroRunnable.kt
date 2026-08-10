package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.context.MacroCallScope

sealed interface MacroRunnable {
    context(scope: MacroCallScope, traceContext: MacroTraceContext)
    fun run()
}

data class CallableMacro(
    val macro: Macro,
    val run: context(MacroCallScope, MacroTraceContext) () -> Unit = { macro.run() }
) : MacroRunnable {
    context(scope: MacroCallScope, traceContext: MacroTraceContext)
    override fun run() = run.invoke(scope, traceContext)
}
