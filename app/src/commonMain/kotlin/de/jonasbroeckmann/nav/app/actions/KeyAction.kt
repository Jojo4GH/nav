package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.State

data class KeyAction(
    val triggers: List<Trigger>,
    private val displayKey: State.() -> KeyboardEvent? = { null },
    private val description: State.() -> String = { "" },
    private val style: State.() -> TextStyle? = { null },
    private val hidden: State.() -> Boolean = { false },
    private val condition: State.() -> Boolean,
    private val action: State.(KeyboardEvent) -> AppAction<*>?
) : Action<KeyboardEvent> {
    constructor(
        vararg keys: KeyboardEvent,
        inQuickMacroMode: Boolean = false,
        displayKey: State.() -> KeyboardEvent? = { keys.firstOrNull() },
        description: State.() -> String = { "" },
        style: State.() -> TextStyle? = { null },
        hidden: State.() -> Boolean = { false },
        condition: State.() -> Boolean,
        action: State.(KeyboardEvent) -> AppAction<*>?
    ) : this(
        triggers = keys.map { Trigger(it, inQuickMacroMode) },
        displayKey = { state.displayKey() },
        description = { state.description() },
        style = { state.style() },
        hidden = { state.hidden() },
        condition = { state.condition() },
        action = { state.action(it) }
    )

    context(stateProvider: StateProvider)
    fun displayKey() = state.displayKey()

    context(stateProvider: StateProvider)
    override fun description() = state.description()

    context(stateProvider: StateProvider)
    override fun style() = state.style()

    context(stateProvider: StateProvider)
    override fun isHidden() = state.hidden()

    context(stateProvider: StateProvider)
    override fun matches(input: KeyboardEvent): Boolean {
        val trigger = Trigger(input, state.inQuickMacroMode)
        return trigger in triggers && isAvailable()
    }

    context(stateProvider: StateProvider)
    override fun isAvailable() = state.condition()

    context(stateProvider: StateProvider)
    override fun run(input: KeyboardEvent) = state.action(input)

    data class Trigger private constructor(
        val key: KeyboardEvent,
        val inQuickMacroMode: Boolean
    ) {
        companion object {
            operator fun invoke(
                key: KeyboardEvent,
                inQuickMacroMode: Boolean
            ) = Trigger(
                key = if (inQuickMacroMode) key.copy(ctrl = false) else key,
                inQuickMacroMode = inQuickMacroMode
            )
        }
    }
}
