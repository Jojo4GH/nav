package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import de.jonasbroeckmann.nav.app.*
import de.jonasbroeckmann.nav.app.macros.DefaultMacro
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeCondition
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeMenuDescription
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.prettyName
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.framework.action.MenuAction
import de.jonasbroeckmann.nav.framework.ui.buildTextFieldContent

class MenuActions(context: FullContext) : FullContext by context {
    @Suppress("detekt:MagicNumber")
    val all = listOf(
        *macros.mapNotNull { macro ->
            if (!macro.enabled) return@mapNotNull null
            if (macro.menuOrder == null) return@mapNotNull null
            macro.menuOrder to MenuAction<State, MainController>(
                description = { macro.computeMenuDescription() },
                style = { macro.style },
                condition = { macro.computeCondition() },
                action = { runMacro(macro) }
            )
        }.toTypedArray(),
        *config.entryMacros.map { macro ->
            500 to MenuAction<State, MainController>(
                description = { currentItem?.let { macro.computeDescription(it) }.orEmpty() },
                style = { currentItem.style },
                hidden = { currentItem == null },
                condition = { macro.computeCondition() },
                action = { runEntryMacro(macro) }
            )
        }.toTypedArray(),
        1000 to MenuAction(
            description = { "Run command here" },
            style = { styles.path },
            condition = { !isTypingCommand },
            action = { updateState { withCommand("") } }
        ),
        1000 to MenuAction(
            description = {
                val commandString = buildTextFieldContent(
                    text = command.orEmpty(),
                    hasFocus = true,
                    placeholder = if (config.hideHints) null else "type command or press ${config.keys.cancel.prettyName} to cancel"
                )
                TextColors.rgb("FFFFFF")("${command}_")
                "${styles.path("❯")} $commandString"
            },
            selectedStyle = null,
            condition = { isTypingCommand },
            action = {
                val command = command
                if (command.isNullOrBlank()) {
                    updateState { withCommand(null) }
                } else {
                    runMacro(DefaultMacro.RunCommand.get())
                }
            }
        ),
    )
        .sortedBy { (order, _) -> order }
        .map { (_, action) -> action }
}
