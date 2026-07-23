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
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import kotlin.reflect.KProperty1

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
data class Macro private constructor(
    @Transient
    private val initializerRecorder: PropertyInitializerRecorder<Macro> = PropertyInitializerRecorder(),
    val id: String? = initializerRecorder.record(Macro::id, null),
    val enabled: Boolean = initializerRecorder.record(Macro::enabled, true),
    val description: StringWithPlaceholders = initializerRecorder.record(Macro::description, Empty),
    val style: StyleString? = initializerRecorder.record(Macro::style, null),
    val key: KeyboardEvent? = initializerRecorder.record(Macro::key, null),
    val hideKey: Boolean = initializerRecorder.record(Macro::hideKey, false),
    val quickModeKey: KeyboardEvent? = initializerRecorder.record(Macro::quickModeKey, null),
    val hideQuickModeKey: Boolean = initializerRecorder.record(Macro::hideQuickModeKey, false),
    val menuOrder: Int? = initializerRecorder.record(Macro::menuOrder, null),
    private val condition: MacroCondition? = initializerRecorder.record(Macro::condition, null),
    @SerialName("run")
    private val actions: MacroActions = initializerRecorder.record(Macro::actions, MacroActions())
) : MacroRunnable {
    constructor(
        id: String? = null,
        enabled: Boolean = true,
        description: StringWithPlaceholders = Empty,
        style: StyleString? = null,
        key: KeyboardEvent? = null,
        hideKey: Boolean = false,
        quickModeKey: KeyboardEvent? = null,
        hideQuickModeKey: Boolean = false,
        menuOrder: Int? = null,
        condition: MacroCondition? = null,
        actions: MacroActions = MacroActions()
    ) : this(
        initializerRecorder = PropertyInitializerRecorder(),
        id = id,
        enabled = enabled,
        description = description,
        style = style,
        key = key,
        hideKey = hideKey,
        quickModeKey = quickModeKey,
        hideQuickModeKey = hideQuickModeKey,
        menuOrder = menuOrder,
        condition = condition,
        actions = actions
    )

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
    
    fun replaceFrom(other: Macro): Macro {
        fun <T> KProperty1<Macro, T>.replace(): T {
            val otherHasExplicitValue = !other.initializerRecorder.usedInitializer(this)
            return get(if (otherHasExplicitValue) other else this@Macro)
        }
        return copy(
            id = Macro::id.replace(),
            enabled = Macro::enabled.replace(),
            description = Macro::description.replace(),
            style = Macro::style.replace(),
            key = Macro::key.replace(),
            hideKey = Macro::hideKey.replace(),
            quickModeKey = Macro::quickModeKey.replace(),
            hideQuickModeKey = Macro::hideQuickModeKey.replace(),
            menuOrder = Macro::menuOrder.replace(),
            condition = Macro::condition.replace(),
            actions = Macro::actions.replace()
        )
    }

    operator fun plus(other: Macro) = replaceFrom(other)

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

private class PropertyInitializerRecorder<This> {
    private val records = mutableSetOf<KProperty1<This, *>>()

    fun <T> record(property: KProperty1<This, T>, default: T): T {
        records += property
        return default
    }

    fun usedInitializer(property: KProperty1<This, *>) = property in records
}
