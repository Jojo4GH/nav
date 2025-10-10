package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.actions.MainActions.Category.*
import de.jonasbroeckmann.nav.app.exit
import de.jonasbroeckmann.nav.app.macros.DefaultMacros
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroSymbolScope
import de.jonasbroeckmann.nav.app.openInEditor
import de.jonasbroeckmann.nav.app.runEntryMacro
import de.jonasbroeckmann.nav.app.runMacro
import de.jonasbroeckmann.nav.app.state.Entry.Type.*
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.prettyName
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.commonPrefix
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.files.SystemFileSystem

class MainActions(context: FullContext) : KeyActions<State, MainController, MainActions.Category>(), FullContext by context {
    enum class Category {
        NormalMode,
        QuickMacroMode
    }

    val cancelQuickMacroMode = QuickMacroMode.registerKeyAction(
        config.keys.cancel.copy(ctrl = false),
        displayKey = { config.keys.cancel },
        description = { "cancel" },
        condition = { inQuickMacroMode },
        action = { updateState { inQuickMacroMode(false) } }
    )

    val quickMacroModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.quickModeKey == null) return@mapNotNull null
        QuickMacroMode.registerKeyAction(
            macro.quickModeKey.copy(ctrl = false),
            displayKey = { macro.quickModeKey },
            description = { macro.computeDescription() },
            style = { macro.style },
            hidden = { macro.hidden },
            condition = { inQuickMacroMode && macro.computeCondition() },
            action = { runMacro(macro) }
        )
    } + config.entryMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        QuickMacroMode.registerKeyAction(
            macro.quickMacroKey.copy(ctrl = false),
            displayKey = { macro.quickMacroKey },
            description = { currentEntry?.let { macro.computeDescription(it) }.orEmpty() },
            style = { currentEntry.style },
            hidden = { currentEntry == null },
            condition = { inQuickMacroMode && macro.condition() },
            action = { runEntryMacro(macro) }
        )
    }

    val menuSubmit = NormalMode.registerKeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.run(null) }
    )

    val normalModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.nonQuickModeKey == null) return@mapNotNull null
        NormalMode.registerKeyAction(
            macro.nonQuickModeKey,
            description = { macro.computeDescription() },
            style = { macro.style },
            hidden = { macro.hidden },
            condition = { macro.computeCondition() },
            action = { runMacro(macro) }
        )
    }

    val cursorUp = NormalMode.registerKeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorShifted(-1) } }
    )
    val cursorDown = NormalMode.registerKeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorShifted(+1) } }
    )
    val cursorHome = NormalMode.registerKeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorCoerced(0) } }
    )
    val cursorEnd = NormalMode.registerKeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorCoerced(filteredItems.lastIndex) } }
    )

    val navigateUp = NormalMode.registerKeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { updateState { navigatedUp() } }
    )
    val navigateInto = NormalMode.registerKeyAction(
        config.keys.nav.into,
        condition = { currentEntry?.type == Directory || currentEntry?.linkTarget?.targetEntry?.type == Directory },
        action = { updateState { navigateTo(currentEntry?.path) } }
    )
    val navigateOpen = NormalMode.registerKeyAction(
        config.keys.nav.open,
        description = { "open in ${editorCommand ?: "editor"}" },
        style = { styles.file },
        condition = { currentEntry?.type == RegularFile || currentEntry?.linkTarget?.targetEntry?.type == RegularFile },
        action = { openInEditor(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
    )

    val discardCommand = NormalMode.registerKeyAction(
        config.keys.cancel,
        description = { "discard command" },
        condition = { isTypingCommand },
        action = { updateState { withCommand(null) } }
    )

    val autocompleteFilter = NormalMode.registerKeyAction(
        config.keys.filter.autocomplete, config.keys.filter.autocomplete.copy(shift = true),
        description = { "autocomplete" },
        condition = { items.isNotEmpty() },
        action = action@{ keyEvent ->
            val commonPrefix = items
                .map { it.path.name.lowercase() }
                .filter { it.startsWith(filter.lowercase()) }
                .ifEmpty { return@action }
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
                return@action updateState { completedState }
            }
            completedState.filteredItems
                .singleOrNull { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                ?.let { singleEntry ->
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingleAfterCompletion) {
                        if (!hasFilterChanged) {
                            return@action updateState { completedState.navigateTo(singleEntry.path) }
                        }
                    }
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingle) {
                        return@action updateState { completedState.navigateTo(singleEntry.path) }
                    }
                }

            updateState { completedState }
        }
    )
    val clearFilter = NormalMode.registerKeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { updateState { withFilter("") } }
    )

    val exitMenu = NormalMode.registerKeyAction(
        config.keys.cancel,
        description = { "close menu" },
        condition = { isMenuOpen },
        action = { updateState { withMenuCursorCoerced(-1) } }
    )
    val closeMenu = NormalMode.registerKeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { updateState { withMenuCursorCoerced(-1) } }
    )
    val openMenu = NormalMode.registerKeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { updateState { withMenuCursorCoerced(0) } }
    )
    val menuDown = NormalMode.registerKeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < shownMenuActions.lastIndex },
        action = { updateState { withMenuCursorCoerced(coercedMenuCursor + 1) } }
    )
    val menuUp = NormalMode.registerKeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { updateState { withMenuCursorCoerced(coercedMenuCursor - 1) } }
    )

    val exitCD = NormalMode.registerKeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { styles.path },
        condition = { directory != WorkingDirectory },
        action = { exit(directory) }
    )
    val exit = NormalMode.registerKeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { true },
        action = { exit(null) }
    )

    val menuActions = listOf(
        *config.macros.map { macro ->
            MenuAction(
                description = { macro.computeDescription() },
                style = { macro.style },
                hidden = { macro.hidden },
                condition = { macro.computeCondition() },
                action = { runMacro(macro) }
            )
        }.toTypedArray(),
        *config.entryMacros.map { macro ->
            MenuAction(
                description = { currentEntry?.let { macro.computeDescription(it) }.orEmpty() },
                style = { currentEntry.style },
                hidden = { currentEntry == null },
                condition = { macro.condition() },
                action = { runEntryMacro(macro) }
            )
        }.toTypedArray(),
        MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { styles.file },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                updateState { withFilter("").updatedEntries(filter) }
            }
        ),
        MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { styles.directory },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                updateState { withFilter("").updatedEntries(filter) }
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
                val currentEntry = requireNotNull(currentEntry)
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

    val normalModeActions = registered[NormalMode].orEmpty()

    val quickMacroModeActions = registered[QuickMacroMode].orEmpty()

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
    private fun Macro.computeDescription() = MacroSymbolScope.empty { description.evaluate() }

    context(state: State)
    private fun Macro.computeCondition() = MacroSymbolScope.empty { available() }
}
