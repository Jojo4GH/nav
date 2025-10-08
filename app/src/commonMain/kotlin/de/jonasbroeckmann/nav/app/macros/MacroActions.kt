package de.jonasbroeckmann.nav.app.macros

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class MacroActions(private val actions: List<MacroAction> = emptyList()) : MacroRunnable {
    constructor(vararg actions: MacroAction) : this(listOf(*actions))

    context(context: MacroRuntimeContext)
    override fun run() {
        actions.forEach { it.run() }
    }
}
