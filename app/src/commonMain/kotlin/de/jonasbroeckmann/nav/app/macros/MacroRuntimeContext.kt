package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import de.jonasbroeckmann.nav.app.App
import de.jonasbroeckmann.nav.app.AppAction

class MacroRuntimeContext private constructor(
    private val app: App
) : MacroVariableScopeBase(app, app) {

    operator fun set(name: String, value: String) {
        variable(name).let {
            if (it is MacroVariable.Mutable) {
                it.set(value)
            } else {
                terminal.danger("Cannot modify '$name' as it is not mutable.")
            }
        }
    }

    fun <R> run(appAction: AppAction<R>) = appAction.runIn(app)

    fun call(newContext: Boolean = false, runnable: MacroRunnable) {
        val callContext = if (newContext) MacroRuntimeContext(app) else this
        try {
            context(callContext) { runnable.run() }
        } catch (_: MacroReturnEvent) {
            /* no-op */
        }
    }

    fun doReturn(): Nothing = throw MacroReturnEvent()

    companion object {
        context(app: App)
        fun run(runnable: MacroRunnable) {
            MacroRuntimeContext(app).call(runnable = runnable)
        }

        private class MacroReturnEvent : Throwable()
    }
}
