package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.AppAction.*
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.DefaultMacros
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroSymbolScope
import de.jonasbroeckmann.nav.app.state.Entry.Type.*
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.prettyName
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.commonPrefix
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.files.SystemFileSystem

typealias MainKeyAction = KeyAction<State, AppAction<*>>

class MainActions(context: FullContext) : KeyActions<State, MainActions.Category, AppAction<*>>(), FullContext by context {
    enum class Category {
        QuickMacroMode
    }

    val cancelQuickMacroMode = MainKeyAction(
        config.keys.cancel.copy(ctrl = false),
        displayKey = { config.keys.cancel },
        description = { "cancel" },
        condition = { inQuickMacroMode },
        action = { UpdateState { inQuickMacroMode(false) } }
    ).registered(QuickMacroMode)

    val quickMacroModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.quickModeKey == null) return@mapNotNull null
        MainKeyAction(
            macro.quickModeKey.copy(ctrl = false),
            displayKey = { macro.quickModeKey },
            description = { macro.computeDescription() },
            style = { macro.computeStyle() },
            hidden = { macro.hidden },
            condition = { inQuickMacroMode && macro.computeCondition() },
            action = { RunMacro(macro) }
        ).registered(QuickMacroMode)
    } + config.entryMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        MainKeyAction(
            macro.quickMacroKey.copy(ctrl = false),
            displayKey = { macro.quickMacroKey },
            description = { currentEntry?.let { macro.computeDescription(it) }.orEmpty() },
            style = { currentEntry.style },
            hidden = { currentEntry == null },
            condition = { inQuickMacroMode && macro.condition() },
            action = { RunEntryMacro(macro) }
        ).registered(QuickMacroMode)
    }

    val menuSubmit = MainKeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.run(null) ?: NoOp }
    ).registered()

    val macroActions = config.macros.mapNotNull { macro ->
        if (macro.nonQuickModeKey == null) return@mapNotNull null
        MainKeyAction(
            macro.nonQuickModeKey,
            description = { macro.computeDescription() },
            style = { macro.computeStyle() },
            hidden = { macro.hidden },
            condition = { macro.computeCondition() },
            action = { RunMacro(macro) }
        ).registered()
    }

    val cursorUp = MainKeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorShifted(-1) } }
    ).registered()
    val cursorDown = MainKeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorShifted(+1) } }
    ).registered()
    val cursorHome = MainKeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorCoerced(0) } }
    ).registered()
    val cursorEnd = MainKeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorCoerced(filteredItems.lastIndex) } }
    ).registered()

    val navigateUp = MainKeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { UpdateState { navigatedUp() } }
    ).registered()
    val navigateInto = MainKeyAction(
        config.keys.nav.into,
        condition = { currentEntry?.type == Directory || currentEntry?.linkTarget?.targetEntry?.type == Directory },
        action = { UpdateState { navigateTo(currentEntry?.path) } }
    ).registered()
    val navigateOpen = MainKeyAction(
        config.keys.nav.open,
        description = { "open in ${editorCommand ?: "editor"}" },
        style = { styles.file },
        condition = { currentEntry?.type == RegularFile || currentEntry?.linkTarget?.targetEntry?.type == RegularFile },
        action = { OpenFile(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
    ).registered()

    val discardCommand = MainKeyAction(
        config.keys.cancel,
        description = { "discard command" },
        condition = { isTypingCommand },
        action = { UpdateState { withCommand(null) } }
    ).registered()

    val autocompleteFilter = MainKeyAction(
        config.keys.filter.autocomplete, config.keys.filter.autocomplete.copy(shift = true),
        description = { "autocomplete" },
        condition = { items.isNotEmpty() },
        action = { keyEvent ->
            val commonPrefix = items
                .map { it.path.name.lowercase() }
                .filter { it.startsWith(filter.lowercase()) }
                .ifEmpty { return@MainKeyAction NoOp }
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
                return@MainKeyAction UpdateState { completedState }
            }
            completedState.filteredItems
                .singleOrNull { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                ?.let { singleEntry ->
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingleAfterCompletion) {
                        if (!hasFilterChanged) {
                            return@MainKeyAction UpdateState { completedState.navigateTo(singleEntry.path) }
                        }
                    }
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingle) {
                        return@MainKeyAction UpdateState { completedState.navigateTo(singleEntry.path) }
                    }
                }

            UpdateState { completedState }
        }
    ).registered()
    val clearFilter = MainKeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { UpdateState { withFilter("") } }
    ).registered()

    val exitMenu = MainKeyAction(
        config.keys.cancel,
        description = { "close menu" },
        condition = { isMenuOpen },
        action = { UpdateState { withMenuCursorCoerced(-1) } }
    ).registered()
    val closeMenu = MainKeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { UpdateState { withMenuCursorCoerced(-1) } }
    ).registered()
    val openMenu = MainKeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { UpdateState { withMenuCursorCoerced(0) } }
    ).registered()
    val menuDown = MainKeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < shownMenuActions.lastIndex },
        action = { UpdateState { withMenuCursorCoerced(coercedMenuCursor + 1) } }
    ).registered()
    val menuUp = MainKeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { UpdateState { withMenuCursorCoerced(coercedMenuCursor - 1) } }
    ).registered()

    val exitCD = MainKeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { styles.path },
        condition = { directory != WorkingDirectory },
        action = { Exit(directory) }
    ).registered()
    val exit = MainKeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { true },
        action = { Exit(null) }
    ).registered()

    val menuActions = listOf(
        *config.macros.map { macro ->
            MenuAction(
                description = { macro.computeDescription() },
                style = { macro.computeStyle() },
                hidden = { macro.hidden },
                condition = { macro.computeCondition() },
                action = { RunMacro(macro) }
            )
        }.toTypedArray(),
        *config.entryMacros.map { macro ->
            MenuAction(
                description = { currentEntry?.let { macro.computeDescription(it) }.orEmpty() },
                style = { currentEntry.style },
                hidden = { currentEntry == null },
                condition = { macro.condition() },
                action = { RunEntryMacro(macro) }
            )
        }.toTypedArray(),
        MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { styles.file },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                UpdateState { withFilter("").updatedEntries(filter) }
            }
        ),
        MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { styles.directory },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                UpdateState { withFilter("").updatedEntries(filter) }
            }
        ),
        MenuAction(
            description = { "Run command here" },
            style = { styles.path },
            condition = { !isTypingCommand },
            action = { UpdateState { withCommand("") } }
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
                    UpdateState { withCommand(null) }
                } else {
                    val macro = identifiedMacros[DefaultMacros.RunCommand.id] ?: DefaultMacros.RunCommand
                    RunMacro(macro)
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
                UpdateState { updatedEntries() }
            }
        ),
    )

    val normalModeActions = registered[null].orEmpty()

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
    private fun Macro.computeStyle() = when {
        dependsOnEntry -> state.currentEntry.style
        dependsOnFilter && state.filter.isNotEmpty() -> styles.filter
        else -> null
    }

    context(state: State)
    private fun Macro.computeCondition() = MacroSymbolScope.empty { available() }
}
