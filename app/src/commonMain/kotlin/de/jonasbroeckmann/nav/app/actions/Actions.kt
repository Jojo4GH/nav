package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.app.AppAction.*
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.DefaultMacroVariable
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroAction
import de.jonasbroeckmann.nav.app.macros.MacroActions
import de.jonasbroeckmann.nav.app.macros.MacroCondition
import de.jonasbroeckmann.nav.app.macros.MacroVariableScope
import de.jonasbroeckmann.nav.app.macros.StringWithPlaceholders
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
                isAvailable() &&
                    registered.asSequence().take(i).none { prioritized ->
                        triggers.any { it in prioritized.triggers } && prioritized.isAvailable()
                    }
            }
        ).also {
            registered += it
        }
    }

    val cursorUp = KeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorShifted(-1) } }
    ).registered()
    val cursorDown = KeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorShifted(+1) } }
    ).registered()
    val cursorHome = KeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorCoerced(0) } }
    ).registered()
    val cursorEnd = KeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { UpdateState { withCursorCoerced(filteredItems.lastIndex) } }
    ).registered()

    val navigateUp = KeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { UpdateState { navigatedUp() } }
    ).registered()
    val navigateInto = KeyAction(
        config.keys.nav.into,
        condition = { currentEntry?.type == Directory || currentEntry?.linkTarget?.targetEntry?.type == Directory },
        action = { UpdateState { navigateTo(currentEntry?.path) } }
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
        action = { UpdateState { withCommand(null) } }
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
                return@KeyAction UpdateState { completedState }
            }
            completedState.filteredItems
                .singleOrNull { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                ?.let { singleEntry ->
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingleAfterCompletion) {
                        if (!hasFilterChanged) {
                            return@KeyAction UpdateState { completedState.navigateTo(singleEntry.path) }
                        }
                    }
                    if (config.autocomplete.autoNavigation == Config.Autocomplete.AutoNavigation.OnSingle) {
                        return@KeyAction UpdateState { completedState.navigateTo(singleEntry.path) }
                    }
                }

            UpdateState { completedState }
        }
    ).registered()
    val clearFilter = KeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { UpdateState { withFilter("") } }
    ).registered()

    val exitMenu = KeyAction(
        config.keys.cancel,
        description = { "close menu" },
        condition = { isMenuOpen },
        action = { UpdateState { withMenuCursorCoerced(-1) } }
    ).registered()
    val closeMenu = KeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { UpdateState { withMenuCursorCoerced(-1) } }
    ).registered()
    val openMenu = KeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { UpdateState { withMenuCursorCoerced(0) } }
    ).registered()
    val menuDown = KeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < availableMenuActions.lastIndex },
        action = { UpdateState { withMenuCursorCoerced(coercedMenuCursor + 1) } }
    ).registered()
    val menuUp = KeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { UpdateState { withMenuCursorCoerced(coercedMenuCursor - 1) } }
    ).registered()

    val menuSubmit = KeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.run(null) }
    ).registered()

    val exitCD = KeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { styles.path },
        condition = { directory != WorkingDirectory },
        action = { Exit(directory) }
    ).registered()
    val exit = KeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { true },
        action = { Exit(null) }
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
                UpdateState { inQuickMacroMode(false) }
            }
        ).registered()
    ) + config.entryMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        KeyAction(
            triggers = listOf(KeyAction.Trigger(key = macro.quickMacroKey, inQuickMacroMode = true)),
            displayKey = { macro.quickMacroKey },
            description = { currentEntry?.let { macro.computeDescription(it) } },
            style = { currentEntry.style },
            condition = { inQuickMacroMode && macro.condition() },
            action = { RunEntryMacro(macro) }
        ).registered()
    } + config.macros.mapNotNull { macro ->
        if (macro.quickModeKey == null) return@mapNotNull null
        KeyAction(
            triggers = listOf(KeyAction.Trigger(key = macro.quickModeKey, inQuickMacroMode = true)),
            displayKey = { macro.quickModeKey },
            description = { macro.computeDescription() },
            style = { macro.computeStyle() },
            condition = { macro.computeCondition() },
            action = { RunMacro(macro) }
        ).registered()
    }

    val macroActions = config.macros.mapNotNull { macro ->
        if (macro.nonQuickModeKey == null) return@mapNotNull null
        KeyAction(
            macro.nonQuickModeKey,
            description = { macro.computeDescription() },
            style = { macro.computeStyle() },
            condition = { macro.computeCondition() },
            action = { RunMacro(macro) }
        ).registered()
    }

    context(state: State)
    private fun Macro.computeDescription() = MacroVariableScope.empty { description?.evaluate() }

    context(state: State)
    private fun Macro.computeStyle() = if (dependsOnEntry) state.currentEntry.style else null

    context(state: State)
    private fun Macro.computeCondition() = MacroVariableScope.empty { available() }

    private val macroMenuActions = listOf(
        config.entryMacros.map { macro ->
            MenuAction(
                description = {
                    currentEntry?.let { "★ " + macro.computeDescription(it) }
                },
                style = { currentEntry.style },
                condition = { macro.condition() },
                action = { RunEntryMacro(macro) }
            )
        },
        config.macros.map { macro ->
            MenuAction(
                description = { MacroVariableScope.empty { macro.description?.evaluate() } },
                style = { if (macro.dependsOnEntry) currentEntry.style else null },
                condition = { MacroVariableScope.empty { macro.available() } },
                action = { RunMacro(macro) }
            )
        }
    ).flatten().toTypedArray()

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

    val menuActions = listOf(
        *macroMenuActions,
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
                    else TextStyles.dim("type command or press ${UI.keyName(config.keys.submit)} to cancel")
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
                    val exitCodeVariable = DefaultMacroVariable.ExitCode.placeholder
                    val macro = namedMacros["runCommand"] ?: Macro(
                        name = "runCommand",
                        hidden = true,
                        actions = MacroActions(
                            MacroAction.RunCommand(command = StringWithPlaceholders(command)),
                            MacroAction.If(
                                condition = MacroCondition.Not(
                                    MacroCondition.Equal(
                                        listOf(
                                            exitCodeVariable,
                                            StringWithPlaceholders("0")
                                        )
                                    )
                                ),
                                then = MacroActions(
                                    MacroAction.Print(
                                        print = StringWithPlaceholders("Received exit code $exitCodeVariable"),
                                        style = MacroAction.Print.Style.Error
                                    )
                                )
                            ),
                            MacroAction.UpdateState(
                                update = MacroAction.UpdateState.StateUpdate(
                                    command = StringWithPlaceholders("")
                                )
                            )
                        )
                    )
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
}
