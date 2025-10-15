package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.framework.context.printlnOnDebug
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MacroActions(private val actions: List<MacroAction> = emptyList()) : MacroRunnable {
    constructor(vararg actions: MacroAction) : this(listOf(*actions))

    context(context: MacroRuntimeContext)
    override fun run() {
        actions.forEach {
            context.printlnOnDebug { "Running macro action: $it" }
            it.run()
        }
    }
}
