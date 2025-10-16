package de.jonasbroeckmann.nav.app.actions

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeCondition
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeQuickModeKeyDescription
import de.jonasbroeckmann.nav.app.runEntryMacro
import de.jonasbroeckmann.nav.app.runMacro
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.framework.action.KeyActions
import de.jonasbroeckmann.nav.framework.input.InputMode

class QuickMacroModeActions(context: FullContext) : KeyActions<State, MainController, Unit>(InputMode.Normal), FullContext by context {
    val cancelQuickMacroMode = registerKeyAction(
        config.keys.cancel.copy(ctrl = false),
        displayKey = { config.keys.cancel },
        description = { "cancel" },
        condition = { true },
        action = { updateState { inQuickMacroMode(false) } }
    )

    val quickMacroModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.quickModeKey == null) return@mapNotNull null
        registerKeyAction(
            macro.quickModeKey.copy(ctrl = false),
            displayKey = { macro.quickModeKey },
            description = { macro.computeQuickModeKeyDescription() },
            style = { macro.style },
            hidden = { macro.hideQuickModeKey },
            condition = { macro.computeCondition() },
            action = { runMacro(macro) }
        )
    } + config.entryMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        registerKeyAction(
            macro.quickMacroKey.copy(ctrl = false),
            displayKey = { macro.quickMacroKey },
            description = { currentItem?.let { macro.computeDescription(it) }.orEmpty() },
            style = { currentItem.style },
            hidden = { currentItem == null },
            condition = { macro.computeCondition() },
            action = { runEntryMacro(macro) }
        )
    }

    val all = registered[Unit].orEmpty()
}
