package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.components.Macro

interface MacroProvider {
    val macros: List<Macro>

    fun macroById(id: String): Macro? = macros.lastOrNull { it.id == id }
}
