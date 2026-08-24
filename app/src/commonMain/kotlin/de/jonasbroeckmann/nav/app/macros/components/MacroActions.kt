package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.MacroTraceElement
import de.jonasbroeckmann.nav.app.macros.context.MacroCallScope
import de.jonasbroeckmann.nav.app.macros.macroTrace
import de.jonasbroeckmann.nav.printlnOnDebug
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MacroActions(private val actions: List<MacroAction> = emptyList()) : MacroRunnable, List<MacroAction> by actions {
    constructor(vararg actions: MacroAction) : this(listOf(*actions))

    context(scope: MacroCallScope, traceContext: MacroTraceContext)
    override fun run() {
        actions.forEachIndexed { i, action ->
            macroTrace({ MacroTraceElement.ActionAtIndex(i, action) }) {
                scope.printlnOnDebug { "Running macro action: $action" }
                action.run()
            }
        }
    }
}
