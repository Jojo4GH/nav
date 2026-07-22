@file:UseSerializers(KeyboardEventAsStringSerializer::class)

package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.config.StyleString
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.utils.KeyboardEventAsStringSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * A macro that can be run in the application.
 *
 * If it is enabled and its [condition] is met, a macro is shown in the following places:
 * - In key hints, if a [key] is set and not [hideKey]
 * - In quick macro mode, if a [quickModeKey] is set and not [hideQuickModeKey]
 * - In the menu, if a [menuOrder] is set
 *
 * @property id An optional identifier for the macro. If set, it can be referenced by other macros.
 * @property enabled Whether the macro is enabled.
 * @property description A human-readable short description of what the macro does. Can contain placeholders for variables.
 * @property style A style for the macro. If null, a default style is computed.
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
    val enabled: Boolean = true,
    val description: StringWithPlaceholders = Empty,
    val style: StyleString? = null,
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
        if (enabled) require(menuOrder == null || description.raw.isNotBlank()) {
            "Macros shown in the menu must have a ${::description.name}"
        }
    }

    context(scope: MacroSymbolScope, traceContext: MacroTraceContext)
    fun available() = condition == null || condition.evaluate()

    private val usedSymbolsInDescriptionOrCondition by lazy {
        description.symbols.toSet() + condition?.usedSymbols.orEmpty()
    }

    private val dependsOnEntry by lazy {
        listOf(
            DefaultMacroProperty.EntryPath,
            DefaultMacroProperty.EntryName,
            DefaultMacroProperty.EntryType
        ).any {
            it.property.symbol in usedSymbolsInDescriptionOrCondition
        }
    }

    private val dependsOnFilter by lazy {
        DefaultMacroProperty.Filter.property.symbol in usedSymbolsInDescriptionOrCondition
    }

    context(context: MacroRuntimeContext, traceContext: MacroTraceContext)
    override fun run() = actions.run()

    companion object {
        context(_: FullContext, _: StateProvider)
        private fun <R> evaluationContext(block: context(MacroSymbolScope, MacroTraceContext) () -> R) = context(
            MacroSymbolScope.Empty,
            MacroTraceContext.Empty,
            block
        )

        context(_: FullContext, _: StateProvider)
        fun Macro.computeKeyDescription() = if (!hideKey) evaluationContext { description.evaluate() } else ""

        context(_: FullContext, _: StateProvider)
        fun Macro.computeQuickModeKeyDescription() = if (!hideQuickModeKey) evaluationContext { description.evaluate() } else ""

        context(_: FullContext, _: StateProvider)
        fun Macro.computeMenuDescription() = if (menuOrder != null) {
            evaluationContext { description.evaluate().replaceFirstChar { it.uppercase() } }
        } else {
            ""
        }

        context(_: StylesProvider, _: StateProvider)
        fun Macro.computeStyle() = when {
            style != null -> style.evaluate()
            dependsOnEntry -> state.currentItem.style
            dependsOnFilter && state.filter.isNotEmpty() -> styles.filter
            else -> TextStyle()
        }

        context(_: FullContext, _: StateProvider)
        fun Macro.computeCondition() = evaluationContext { available() }
    }
}
