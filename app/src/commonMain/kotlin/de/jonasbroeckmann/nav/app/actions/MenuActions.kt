package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.DefaultMacros
import de.jonasbroeckmann.nav.app.macros.computeCondition
import de.jonasbroeckmann.nav.app.macros.computeDescription
import de.jonasbroeckmann.nav.app.runEntryMacro
import de.jonasbroeckmann.nav.app.runMacro
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.prettyName
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.framework.action.MenuAction
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.files.SystemFileSystem
import kotlin.collections.get

class MenuActions(context: FullContext) : FullContext by context {
    val all = listOf<MenuAction<State, MainController>>(
        *config.macros.map { macro ->
            MenuAction<State, MainController>(
                description = { macro.computeDescription() },
                style = { macro.style },
                hidden = { macro.hidden },
                condition = { macro.computeCondition() },
                action = { runMacro(macro) }
            )
        }.toTypedArray(),
        *config.entryMacros.map { macro ->
            MenuAction<State, MainController>(
                description = { currentItem?.let { macro.computeDescription(it) }.orEmpty() },
                style = { currentItem.style },
                hidden = { currentItem == null },
                condition = { macro.computeCondition() },
                action = { runEntryMacro(macro) }
            )
        }.toTypedArray(),
        MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { styles.file },
            condition = { filter.isNotEmpty() && !unfilteredItems.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                updateState { withFilter("").updatedEntries { it.path.name == filter } }
            }
        ),
        MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { styles.directory },
            condition = { filter.isNotEmpty() && !unfilteredItems.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                updateState { withFilter("").updatedEntries { it.path.name == filter } }
            }
        ),
        MenuAction(
            description = { "Run command here" },
            style = { styles.path },
            condition = { !isTypingCommand },
            action = { updateState { withCommand("") } }
        ),
        MenuAction(
            description = {
                val cmdStr = if (command.isNullOrEmpty()) {
                    if (config.hideHints) ""
                    else TextStyles.dim("type command or press ${config.keys.cancel.prettyName} to cancel")
                } else {
                    TextColors.rgb("FFFFFF")("${command}_")
                }
                "${styles.path("❯")} $cmdStr"
            },
            selectedStyle = null,
            condition = { isTypingCommand },
            action = {
                val command = command
                if (command.isNullOrBlank()) {
                    updateState { withCommand(null) }
                } else {
                    val macro = identifiedMacros[DefaultMacros.RunCommand.id] ?: DefaultMacros.RunCommand
                    runMacro(macro)
                }
            }
        ),
        MenuAction(
            description = {
                val currentEntry = currentItem
                requireNotNull(currentEntry)
                val style = when (currentEntry.type) {
                    SymbolicLink -> styles.link
                    Directory -> styles.directory
                    RegularFile -> styles.file
                    Unknown -> TextColors.magenta
                }
                style("Delete: ${currentEntry.path.name}")
            },
            condition = { currentItem.let { it != null && it.type != Directory } },
            action = {
                val currentEntry = requireNotNull(currentItem)
                when (currentEntry.type) {
                    SymbolicLink -> SystemFileSystem.delete(currentEntry.path)
                    Directory -> SystemFileSystem.delete(currentEntry.path)
                    RegularFile -> SystemFileSystem.delete(currentEntry.path)
                    Unknown -> { /* no-op */
                    }
                }
                updateState { updatedEntries() }
            }
        ),
    )
}
