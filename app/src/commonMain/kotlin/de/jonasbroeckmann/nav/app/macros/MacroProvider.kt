package de.jonasbroeckmann.nav.app.macros

interface MacroProvider {
    val macros: List<Macro>

    fun macroById(id: String): Macro? = macros.lastOrNull { it.id == id }
}
