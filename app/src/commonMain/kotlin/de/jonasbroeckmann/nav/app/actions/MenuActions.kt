package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import de.jonasbroeckmann.nav.app.*
import de.jonasbroeckmann.nav.app.macros.DefaultMacros
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeCondition
import de.jonasbroeckmann.nav.app.macros.Macro.Companion.computeMenuDescription
import de.jonasbroeckmann.nav.app.state.Entry.Type.*
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.prettyName
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.framework.action.MenuAction
import de.jonasbroeckmann.nav.framework.ui.buildTextFieldContent
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.files.SystemFileSystem

class MenuActions(context: FullContext) : FullContext by context {
    @Suppress("detekt:MagicNumber")
    val all = listOf(
        *config.macros.mapNotNull { macro ->
            if (macro.menuOrder == null) return@mapNotNull null
            macro.menuOrder to MenuAction<State, MainController>(
                description = { macro.computeMenuDescription() },
                style = { macro.style },
                condition = { macro.computeCondition() },
                action = { runMacro(macro) }
            )
        }.toTypedArray(),
        *config.entryMacros.map { macro ->
            100 to MenuAction<State, MainController>(
                description = { currentItem?.let { macro.computeDescription(it) }.orEmpty() },
                style = { currentItem.style },
                hidden = { currentItem == null },
                condition = { macro.computeCondition() },
                action = { runEntryMacro(macro) }
            )
        }.toTypedArray(),
        200 to MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { styles.file },
            condition = { filter.isNotEmpty() && !unfilteredItems.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                updateState { withFilter("").updatedEntries { it.path.name == filter } }
            }
        ),
        200 to MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { styles.directory },
            condition = { filter.isNotEmpty() && !unfilteredItems.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                updateState { withFilter("").updatedEntries { it.path.name == filter } }
            }
        ),
        300 to MenuAction(
            description = { "Run command here" },
            style = { styles.path },
            condition = { !isTypingCommand },
            action = { updateState { withCommand("") } }
        ),
        300 to MenuAction(
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
                    val macro = identifiedMacros[DefaultMacros.RunCommand.id] ?: DefaultMacros.RunCommand
                    runMacro(macro)
                }
            }
        ),
        400 to MenuAction(
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
                    Unknown -> { /* no-op */ }
                }
                updateState { updatedEntries() }
            }
        ),
    )
        .sortedBy { (order, _) -> order }
        .map { (_, action) -> action }
}
