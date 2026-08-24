package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.macros.components.Macro

interface MacroProvider {
    val macros: List<Macro>

    fun macro(id: Macro.Id): Macro? = macros.lastOrNull { it.id == id }

    companion object {
        operator fun invoke(
            macros: List<Macro>
        ): MacroProvider = MacroProviderImpl(macros)

        operator fun MacroProvider.plus(other: MacroProvider): MacroProvider = MacroProviderImpl(macros + other.macros)

        fun MacroProvider.onLoaded(onLoaded: (List<Macro>) -> Unit) = object : MacroProvider {
            override val macros by lazy {
                this@onLoaded.macros.also(onLoaded)
            }

            override fun macro(id: Macro.Id) = this@onLoaded.macro(id)
        }
    }
}

private class MacroProviderImpl(
    macros: List<Macro>
) : MacroProvider {
    override val macros by lazy {
        buildList {
            macros.forEach { macro ->
                if (macro.id == null) {
                    add(macro)
                } else {
                    val merged = filter { it.id == macro.id }
                        .reduceOrNull { a, b -> a + b }
                        ?.let { it + macro }
                        ?: macro
                    removeAll { it.id == macro.id }
                    add(merged)
                }
            }
        }
    }

    private val identifiedMacros by lazy {
        macros
            .mapNotNull { macro -> macro.id?.let { it to macro } }
            .toMap()
    }

    override fun macro(id: Macro.Id) = identifiedMacros[id]
}
