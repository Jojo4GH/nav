package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.state.State

data class KeyAction(
    val triggers: List<Trigger>,
    val displayKey: (State) -> KeyboardEvent? = { null },
    override val description: State.() -> String? = { null },
    private val style: State.() -> TextStyle? = { null },
    private val condition: State.() -> Boolean,
    private val action: State.(KeyboardEvent) -> AppAction<*>?
) : Action<KeyboardEvent> {
    constructor(
        vararg keys: KeyboardEvent,
        displayKey: (State) -> KeyboardEvent? = { keys.firstOrNull() },
        description: State.() -> String? = { null },
        style: State.() -> TextStyle? = { null },
        condition: State.() -> Boolean,
        action: State.(KeyboardEvent) -> AppAction<*>?
    ) : this(
        triggers = keys.map { Trigger(it, false) },
        displayKey = displayKey,
        description = description,
        style = style,
        condition = condition,
        action = action
    )

    context(state: State)
    override fun style() = state.style()

    override fun matches(state: State, input: KeyboardEvent): Boolean {
        val trigger = Trigger(input, state.inQuickMacroMode)
        return trigger in triggers && isAvailable(state)
    }

    override fun isAvailable(state: State) = state.condition()

    override fun run(state: State, input: KeyboardEvent) = state.action(input)

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
