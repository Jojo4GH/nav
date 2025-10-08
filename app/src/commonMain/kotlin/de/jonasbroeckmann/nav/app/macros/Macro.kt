@file:UseSerializers(KeyboardEventAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.input.KeyboardEvent
import de.jonasbroeckmann.nav.utils.KeyboardEventAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * A macro that can be run in the application.
 *
 * If their [condition] is met, macros are shown in the following places:
 * - In quick macro mode, if a [quickModeKey] is set and is not [hidden]
 * - In key hints, if a [nonQuickModeKey] is set and is not [hidden]
 * - In the menu, if not [hidden]
 *
 * @property id An optional identifier for the macro. If set, it can be referenced by other macros.
 * @property description A human-readable short description of what the macro does. Can contain placeholders for variables.
 * @property hidden If true, the macro will not be shown.
 * @property quickModeKey The key that triggers the macro when in quick macro mode.
 * @property nonQuickModeKey The key that triggers the macro when not in quick macro mode.
 * @property condition An optional condition that must be met for the macro to be available.
 * @property actions The actions to run when the macro is executed.
 */
@Serializable
data class Macro(
    val id: String? = null,
    val description: StringWithPlaceholders = Empty,
    val hidden: Boolean = false,
    val quickModeKey: KeyboardEvent? = null,
    val nonQuickModeKey: KeyboardEvent? = null,
    val condition: MacroCondition? = null,
    @SerialName("run")
    val actions: MacroActions = MacroActions()
) : MacroRunnable {
    init {
        require(hidden || description.raw.isNotBlank()) {
            "Non-hidden macros must have a ${::description.name}"
        }
    }

    context(scope: MacroVariableScope)
    fun available() = condition == null || condition.evaluate()

    private val usedVariablesInDescriptionOrCondition by lazy {
        description.placeholders.toSet() + condition?.usedVariables.orEmpty()
    }

    val dependsOnEntry by lazy {
        listOf(
            DefaultMacroVariables.EntryPath,
            DefaultMacroVariables.EntryName,
            DefaultMacroVariables.EntryType
        ).any {
            it.label in usedVariablesInDescriptionOrCondition
        }
    }

    val dependsOnFilter by lazy {
        DefaultMacroVariables.Filter.label in usedVariablesInDescriptionOrCondition
    }

    context(context: MacroRuntimeContext)
    override fun run() = actions.run()
}
