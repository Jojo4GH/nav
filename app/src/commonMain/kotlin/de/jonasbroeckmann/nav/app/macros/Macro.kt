@file:UseSerializers(KeyboardEventAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.utils.KeyboardEventAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * A macro that can be run in the application.
 *
 * If their [condition] is met, macros are shown in the following places:
 * - In key hints, if a [key] is set and not [hideKey]
 * - In quick macro mode, if a [quickModeKey] is set and not [hideQuickModeKey]
 * - In the menu, if a [menuOrder] is set
 *
 * @property id An optional identifier for the macro. If set, it can be referenced by other macros.
 * @property description A human-readable short description of what the macro does. Can contain placeholders for variables.
 * @property key The key that triggers the macro when in normal mode.
 * @property hideKey Whether to hide the hint for the normal mode key.
 * @property quickModeKey The key that triggers the macro when in quick macro mode.
 * @property hideQuickModeKey Whether to hide the hint for the quick macro mode key.
 * @property menuOrder If not null, the macro is shown in the menu. Lower numbers appear first.
 * @property condition An optional condition that must be met for the macro to be available.
 * @property actions The actions to run when the macro is executed.
 */
@Serializable
data class Macro(
    val id: String? = null,
    val description: StringWithPlaceholders = Empty,
    val key: KeyboardEvent? = null,
    val hideKey: Boolean = false,
    val quickModeKey: KeyboardEvent? = null,
    val hideQuickModeKey: Boolean = false,
    val menuOrder: Int? = null,
    private val condition: MacroCondition? = null,
    @SerialName("run")
    private val actions: MacroActions = MacroActions()
) : MacroRunnable {
    init {
        require(menuOrder == null || description.raw.isNotBlank()) {
            "Macros shown in the menu must have a ${::description.name}"
        }
    }

    context(scope: MacroSymbolScope)
    fun available() = condition == null || condition.evaluate()

    private val usedSymbolsInDescriptionOrCondition by lazy {
        description.symbols.toSet() + condition?.usedSymbols.orEmpty()
    }

    val dependsOnEntry by lazy {
        listOf(
            DefaultMacroProperties.EntryPath,
            DefaultMacroProperties.EntryName,
            DefaultMacroProperties.EntryType
        ).any {
            it.property.symbol in usedSymbolsInDescriptionOrCondition
        }
    }

    val dependsOnFilter by lazy {
        DefaultMacroProperties.Filter.property.symbol in usedSymbolsInDescriptionOrCondition
    }

    context(context: MacroRuntimeContext)
    override fun run() = actions.run()

    companion object {
        context(_: FullContext, _: StateProvider)
        fun Macro.computeKeyDescription() = if (!hideKey) MacroSymbolScope.empty { description.evaluate() } else ""

        context(_: FullContext, _: StateProvider)
        fun Macro.computeQuickModeKeyDescription() = if (!hideQuickModeKey) MacroSymbolScope.empty { description.evaluate() } else ""

        context(_: FullContext, _: StateProvider)
        fun Macro.computeMenuDescription() = if (menuOrder != null) {
            MacroSymbolScope.empty { description.evaluate().replaceFirstChar { it.uppercase() } }
        } else {
            ""
        }

        context(_: FullContext, _: StateProvider)
        fun Macro.computeCondition() = MacroSymbolScope.empty { available() }
    }
}
