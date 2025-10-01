package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.App.Event.*
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.state.Entry.Type.*
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.UI
import de.jonasbroeckmann.nav.app.ui.UI.Companion.style
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.commonPrefix
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.files.SystemFileSystem

class Actions(context: FullContext) : FullContext by context {
    private val registered = mutableListOf<KeyAction>()
    val ordered: List<KeyAction> get() = registered

    private fun KeyAction.registered(): KeyAction {
        val i = registered.size
        return copy(
            condition = {
                isAvailable(this) &&
                    registered.asSequence().take(i).none { prioritized ->
                        triggers.any { it in prioritized.triggers } && prioritized.isAvailable(this)
                    }
            }
        ).also {
            registered += it
        }
    }

    val cursorUp = KeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursorShifted(-1)) }
    ).registered()
    val cursorDown = KeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursorShifted(+1)) }
    ).registered()
    val cursorHome = KeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursorCoerced(0)) }
    ).registered()
    val cursorEnd = KeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursorCoerced(filteredItems.lastIndex)) }
    ).registered()

    val navigateUp = KeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { NewState(navigatedUp()) }
    ).registered()
    val navigateInto = KeyAction(
        config.keys.nav.into,
        condition = { currentEntry?.type == Directory || currentEntry?.linkTarget?.targetEntry?.type == Directory },
        action = { NewState(navigatedInto(currentEntry)) }
    ).registered()
    val navigateOpen = KeyAction(
        config.keys.nav.open,
        description = { "open in ${editorCommand ?: "editor"}" },
        style = { styles.file },
        condition = { currentEntry?.type == RegularFile || currentEntry?.linkTarget?.targetEntry?.type == RegularFile },
        action = { OpenFile(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
    ).registered()

    val discardCommand = KeyAction(
        config.keys.cancel,
        description = { "discard command" },
        condition = { isTypingCommand },
        action = { NewState(withCommand(null)) }
    ).registered()

    val autocompleteFilter = KeyAction(
        config.keys.filter.autocomplete, config.keys.filter.autocomplete.copy(shift = true),
        description = { "autocomplete" },
        condition = { items.isNotEmpty() },
        action = { keyEvent ->
            val commonPrefix = items
                .map { it.path.name.lowercase() }
                .filter { it.startsWith(filter.lowercase()) }
                .ifEmpty { return@KeyAction null }
                .commonPrefix()

            val filteredState = withFilter(commonPrefix)
            val hasFilterChanged = !filteredState.filter.equals(filter, ignoreCase = true)

            // Handle autocomplete
            val completedState = when (config.autocomplete.style) {
                CommonPrefixStop -> {
                    filteredState.withCursorOnFirst { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                }
                CommonPrefixCycle -> {
                    if (hasFilterChanged) {
                        // Go to first
                        filteredState.withCursorOnFirst { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                    } else {
                        if (keyEvent.shift) {
                            // Go to previous
                            filteredState.withCursorOnNextReverse { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                        } else {
                            // Go to next
                            filteredState.withCursorOnNext { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                        }
                    }
                }
            }

            // Handle auto-navigation
            if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.None) {
                return@KeyAction NewState(completedState)
            }
            completedState.filteredItems
                .singleOrNull { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                ?.let { singleEntry ->
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingleAfterCompletion) {
                        if (!hasFilterChanged) {
                            return@KeyAction NewState(completedState.navigatedInto(singleEntry))
                        }
                    }
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingle) {
                        return@KeyAction NewState(completedState.navigatedInto(singleEntry))
                    }
                }

            NewState(completedState)
        }
    ).registered()
    val clearFilter = KeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { NewState(withFilter("")) }
    ).registered()

    val exitMenu = KeyAction(
        config.keys.cancel,
        description = { "close menu" },
        condition = { isMenuOpen },
        action = { NewState(withMenuCursor(null)) }
    ).registered()
    val closeMenu = KeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { NewState(withMenuCursor(null)) }
    ).registered()
    val openMenu = KeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { NewState(withMenuCursor(0)) }
    ).registered()
    val menuDown = KeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < availableMenuActions.lastIndex },
        action = { NewState(withMenuCursor(coercedMenuCursor + 1)) }
    ).registered()
    val menuUp = KeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { NewState(withMenuCursor(coercedMenuCursor - 1)) }
    ).registered()

    val menuSubmit = KeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.perform(this, null) }
    ).registered()

    val exitCD = KeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { styles.path },
        condition = { directory != WorkingDirectory },
        action = { ExitAt(directory) }
    ).registered()
    val exit = KeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { true },
        action = { Exit }
    ).registered()

    val quickMacroActions = listOf(
        KeyAction(
            triggers = listOf(KeyAction.Trigger(key = config.keys.cancel, inQuickMacroMode = true)),
            displayKey = { config.keys.cancel },
            description = {
                "cancel"
            },
            condition = {
                inQuickMacroMode
            },
            action = {
                NewState(inQuickMacroMode(false))
            }
        ).registered()
    ) + config.entryMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        KeyAction(
            triggers = listOf(KeyAction.Trigger(key = macro.quickMacroKey, inQuickMacroMode = true)),
            displayKey = { macro.quickMacroKey },
            description = {
                currentEntry?.let { macro.computeDescription(it) }
            },
            style = { currentEntry.style },
            condition = {
                inQuickMacroMode && macro.condition()
            },
            action = {
                macro.runCommand()
            }
        ).registered()
    }

    private val macroMenuActions = config.entryMacros.map { macro ->
        MenuAction(
            description = {
                currentEntry?.let { "* " + macro.computeDescription(it) }
            },
            style = { currentEntry.style },
            condition = {
                macro.condition()
            },
            action = {
                macro.runCommand()
            }
        )
    }.toTypedArray()

    context(state: State)
    private fun Config.EntryMacro.condition(): Boolean {
        val currentEntry = state.currentEntry
        return when (currentEntry?.type) {
            null -> false
            SymbolicLink -> onSymbolicLink
            Directory -> onDirectory
            RegularFile -> onFile
            Unknown -> false
        }
    }

    context(state: State)
    private fun Config.EntryMacro.runCommand() = RunMacroCommand(
        command = computeCommand(requireNotNull(state.currentEntry)),
        eventAfterSuccessfulCommand = when (afterSuccessfulCommand) {
            Config.AfterMacroCommand.DoNothing -> null
            Config.AfterMacroCommand.ExitAtCurrentDirectory -> ExitAt(state.directory)
            Config.AfterMacroCommand.ExitAtInitialDirectory -> Exit
        },
        eventAfterFailedCommand = when (afterFailedCommand) {
            Config.AfterMacroCommand.DoNothing -> null
            Config.AfterMacroCommand.ExitAtCurrentDirectory -> ExitAt(state.directory)
            Config.AfterMacroCommand.ExitAtInitialDirectory -> Exit
        }
    )

    val menuActions = listOf(
        *macroMenuActions,
        MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { styles.file },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                NewState(withFilter("").updatedEntries(filter))
            }
        ),
        MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { styles.directory },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                NewState(withFilter("").updatedEntries(filter))
            }
        ),
        MenuAction(
            description = { "Run command here" },
            style = { styles.path },
            condition = { !isTypingCommand },
            action = { NewState(withCommand("")) }
        ),
        MenuAction(
            description = {
                val cmdStr = if (command.isNullOrEmpty()) {
                    if (config.hideHints) ""
                    else TextStyles.dim("type command or press ${UI.keyName(config.keys.submit)} to cancel")
                } else {
                    TextColors.rgb("FFFFFF")("${command}_")
                }
                "${styles.path("❯")} $cmdStr"
            },
            selectedStyle = null,
            condition = { isTypingCommand },
            action = {
                if (command.isNullOrBlank()) NewState(withCommand(null)) else RunCommand
            }
        ),
        MenuAction(
            description = {
                val currentEntry = currentEntry
                requireNotNull(currentEntry)
                val style = when (currentEntry.type) {
                    SymbolicLink -> styles.link
                    Directory -> styles.directory
                    RegularFile -> styles.file
                    Unknown -> TextColors.magenta
                }
                style("Delete: ${currentEntry.path.name}")
            },
            condition = { currentEntry.let { it != null && it.type != Directory } },
            action = {
                val currentEntry = currentEntry
                requireNotNull(currentEntry)
                when (currentEntry.type) {
                    SymbolicLink -> SystemFileSystem.delete(currentEntry.path)
                    Directory -> SystemFileSystem.delete(currentEntry.path)
                    RegularFile -> SystemFileSystem.delete(currentEntry.path)
                    Unknown -> { /* no-op */ }
                }
                NewState(updatedEntries())
            }
        ),
    )
}
