package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.context.MacroCallScope

interface MacroCallable : MacroRunnable {
    val macro: Macro

    companion object {
        operator fun invoke(
            macro: Macro,
            run: MacroRunnable.Action = { macro.run() }
        ) = object : MacroCallable {
            override val macro get() = macro

            context(scope: MacroCallScope, traceContext: MacroTraceContext)
            override fun run() = run.invoke(scope, traceContext)
        }
    }
}
