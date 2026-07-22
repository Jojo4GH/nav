package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.command.printlnOnDebug
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MacroActions(private val actions: List<MacroAction> = emptyList()) : MacroRunnable {
    constructor(vararg actions: MacroAction) : this(listOf(*actions))

    context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
    override fun run() {
        actions.forEachIndexed { i, action ->
            macroTrace({ MacroTraceElement.ActionAtIndex(i, action) }) {
                context.printlnOnDebug { "Running macro action: $action" }
                action.run()
            }
        }
    }
}
