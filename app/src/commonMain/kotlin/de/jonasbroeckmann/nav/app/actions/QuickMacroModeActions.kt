package de.jonasbroeckmann.nav.app.actions

import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.computeCondition
import de.jonasbroeckmann.nav.app.macros.computeDescription
import de.jonasbroeckmann.nav.app.runEntryMacro
import de.jonasbroeckmann.nav.app.runMacro
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.app.updateState

class QuickMacroModeActions(context: FullContext) : KeyActions<State, MainController, Unit>(), FullContext by context {
    val cancelQuickMacroMode = registerKeyAction(
        config.keys.cancel.copy(ctrl = false),
        displayKey = { config.keys.cancel },
        description = { "cancel" },
        condition = { true },
        action = { updateState { withInputMode(Normal) } }
    )

    val quickMacroModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.quickModeKey == null) return@mapNotNull null
        registerKeyAction(
            macro.quickModeKey.copy(ctrl = false),
            displayKey = { macro.quickModeKey },
            description = { macro.computeDescription() },
            style = { macro.style },
            hidden = { macro.hidden },
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
