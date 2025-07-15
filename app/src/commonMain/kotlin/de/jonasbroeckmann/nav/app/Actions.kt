package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.info
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.ConfigProvider
import de.jonasbroeckmann.nav.NavCommand
import de.jonasbroeckmann.nav.app.App.Event.*
import de.jonasbroeckmann.nav.app.UI.Companion.macroStyle
import de.jonasbroeckmann.nav.app.UI.Companion.style
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.commonPrefix
import de.jonasbroeckmann.nav.utils.div
import kotlinx.io.IOException
import kotlinx.io.files.SystemFileSystem

class Actions(config: Config) : ConfigProvider by config {
    val cursorUp = KeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(cursor - 1)) }
    )
    val cursorDown = KeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(cursor + 1)) }
    )
    val cursorHome = KeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(0)) }
    )
    val cursorEnd = KeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(filteredItems.lastIndex)) }
    )

    val navigateUp = KeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { NewState(navigatedUp()) }
    )
    val navigateInto = KeyAction(
        config.keys.nav.into,
        condition = { currentEntry?.isDirectory == true },
        action = { NewState(navigatedInto(currentEntry)) }
    )
    val navigateOpen = KeyAction(
        config.keys.nav.open,
        description = { "open in ${config.editor}" },
        style = { TextColors.rgb(config.colors.file) },
        condition = { currentEntry?.isRegularFile == true },
        action = { OpenFile(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
    )

    val exitCD = KeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { TextColors.rgb(config.colors.path) },
        condition = { directory != WorkingDirectory && filter.isEmpty() && !isMenuOpen },
        action = { ExitAt(directory) }
    )
    val exit = KeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { filter.isEmpty() },
        action = { Exit }
    )

    val autocompleteFilter = KeyAction(
        config.keys.filter.autocomplete, config.keys.filter.autocomplete.copy(shift = true),
        description = { "autocomplete" },
        condition = { items.isNotEmpty() },
        action = { keyEvent ->
            val commonPrefix = items
                .map { it.path.name.lowercase() }
                .filter { it.startsWith(filter.lowercase()) }
                .commonPrefix()
            val filteredState = filtered(commonPrefix)
            val hasFilterChanged = !filteredState.filter.equals(filter, ignoreCase = true)

            // Handle autocomplete
            val completedState = when (config.autocomplete.style) {
                Config.Autocomplete.Style.CommonPrefixStop -> {
                    filteredState.withCursorOnFirst { it.path.name.startsWith(commonPrefix, ignoreCase = true) }
                }
                Config.Autocomplete.Style.CommonPrefixCycle -> {
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
    )
    val clearFilter = KeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { NewState(filtered("")) }
    )

    val openMenu = KeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { NewState(withMenuCursor(0)) }
    )
    val closeMenu = KeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { NewState(withMenuCursor(null)) }
    )
    val menuDown = KeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < availableMenuActions.lastIndex },
        action = { NewState(withMenuCursor(coercedMenuCursor + 1)) }
    )
    val menuUp = KeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { NewState(withMenuCursor(coercedMenuCursor - 1)) }
    )

    val quickMacroActions = listOf(
        KeyAction(
            keyFilter = { state, key ->
                state.inQuickMacroMode && key.copy(ctrl = false) == config.keys.cancel
            },
            displayKey = { config.keys.cancel },
            description = { "cancel" },
            condition = { inQuickMacroMode },
            action = { NewState(copy(inQuickMacroMode = false)) }
        )
    ) + config.allMacros.mapNotNull { macro ->
        if (macro.quickMacroKey == null) return@mapNotNull null
        KeyAction(
            keyFilter = { state, key ->
                state.inQuickMacroMode && key.copy(ctrl = false) == macro.quickMacroKey
            },
            displayKey = { macro.quickMacroKey },
            description = { macro.computeDescription() },
            style = { macroStyle(macro) },
            condition = { inQuickMacroMode && macro.isAvailable() },
            action = { macro.computeAppEvent() }
        )
    }

    private val macroMenuActions = config.allMacros.map { macro ->
        MenuAction(
            description = { "* ${macro.computeDescription()}" },
            style = { macroStyle(macro) },
            condition = { macro.isAvailable() },
            action = { macro.computeAppEvent() }
        )
    }.toTypedArray()

    context(state: State)
    private fun Config.Macro.computeAppEvent() = RunMacroCommand(
        command = computeCommand(),
        eventAfterSuccessfulCommand = { afterSuccessfulCommand.computeEvent() },
        eventAfterFailedCommand = { afterFailedCommand.computeEvent() }
    )

    val menuActions = listOf(
        *macroMenuActions,
        MenuAction(
            description = { "New file: \"${filter}\"" },
            style = { TextColors.rgb(config.colors.file) },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.sink(directory / filter).close()
                NewState(filtered("").updatedEntries(filter))
            }
        ),
        MenuAction(
            description = { "New directory: \"${filter}\"" },
            style = { TextColors.rgb(config.colors.directory) },
            condition = { filter.isNotEmpty() && !items.any { it.path.name == filter } },
            action = {
                SystemFileSystem.createDirectories(directory / filter)
                NewState(filtered("").updatedEntries(filter))
            }
        ),
        MenuAction(
            description = { "Run command here" },
            style = { TextColors.rgb(config.colors.path) },
            condition = { !isTypingCommand },
            action = { NewState(withCommand("")) }
        ),
        MenuAction(
            description = {
                val cmdStr = if (command.isNullOrBlank()) {
                    if (config.hideHints) ""
                    else TextStyles.dim("type command or press ${UI.keyName(config.keys.submit)} to cancel")
                } else {
                    TextColors.rgb("FFFFFF")(command)
                }
                "${TextColors.rgb(config.colors.path)("❯")} $cmdStr"
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
                val style = when {
                    currentEntry.isDirectory -> TextColors.rgb(config.colors.directory)
                    currentEntry.isRegularFile -> TextColors.rgb(config.colors.file)
                    currentEntry.isSymbolicLink -> TextColors.rgb(config.colors.link)
                    else -> TextColors.magenta
                }
                style("Delete: ${currentEntry.path.name}")
            },
            condition = { currentEntry.let { it != null && !it.isDirectory } },
            action = {
                val currentEntry = currentEntry
                requireNotNull(currentEntry)
                when {
                    currentEntry.isDirectory -> SystemFileSystem.delete(currentEntry.path)
                    currentEntry.isRegularFile -> SystemFileSystem.delete(currentEntry.path)
                    currentEntry.isSymbolicLink -> SystemFileSystem.delete(currentEntry.path)
                }
                NewState(updatedEntries())
            }
        ),
    )

    val menuSubmit = KeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.perform(this, null) }
    )

    val ordered = listOf(
        cursorUp, cursorDown, cursorHome, cursorEnd,
        navigateUp, navigateInto, navigateOpen,
        exitCD, exit,
        autocompleteFilter, clearFilter,
        menuDown, menuUp, openMenu, closeMenu, menuSubmit,
    )
}

sealed interface Action<Event : InputEvent?> {
    val description: State.() -> String?

    context(state: State) fun style(): TextStyle?

    fun matches(state: State, input: Event): Boolean
    fun isAvailable(state: State): Boolean

    fun perform(state: State, input: Event): App.Event?

}

data class MenuAction(
    override val description: State.() -> String?,
    private val style: State.() -> TextStyle? = { null },
    val selectedStyle: TextStyle? = TextStyles.inverse.style,
    private val condition: State.() -> Boolean,
    private val action: State.() -> App.Event?
) : Action<Nothing?> {
    context(state: State)
    override fun style() = state.style()

    override fun matches(state: State, input: Nothing?) = isAvailable(state)
    override fun isAvailable(state: State) = state.condition()

    override fun perform(state: State, input: Nothing?) = state.action()
}

data class KeyAction(
    val keyFilter: (State, KeyboardEvent) -> Boolean,
    val displayKey: (State) -> KeyboardEvent? = { null },
    override val description: State.() -> String? = { null },
    private val style: State.() -> TextStyle? = { null },
    private val condition: State.() -> Boolean,
    private val action: State.(KeyboardEvent) -> App.Event?
) : Action<KeyboardEvent> {
    constructor(
        vararg keys: KeyboardEvent,
        displayKey: (State) -> KeyboardEvent? = { keys.firstOrNull() },
        description: State.() -> String? = { null },
        style: State.() -> TextStyle? = { null },
        condition: State.() -> Boolean,
        action: State.(KeyboardEvent) -> App.Event?
    ) : this(
        keyFilter = { _, key -> keys.any { it == key } },
        displayKey = displayKey,
        description = description,
        style = style,
        condition = condition,
        action = action
    )

    context(state: State)
    override fun style() = state.style()

    override fun matches(state: State, input: KeyboardEvent) = keyFilter(state, input) && isAvailable(state)
    override fun isAvailable(state: State) = state.condition()

    override fun perform(state: State, input: KeyboardEvent) = state.action(input)
}

fun <E : InputEvent?> Action<E>.tryPerform(state: State, input: E, terminal: Terminal): App.Event? {
    try {
        return perform(state, input)
    } catch (e: IOException) {
        val msg = e.message
        when {
            msg == null -> {
                terminal.danger("An unknown error occurred")
                terminal.info("If this should be considered a bug, please report it.")
                return null
            }
            msg.contains("Permission denied", ignoreCase = true) -> {
                terminal.danger("$msg")
                terminal.info("Try running ${NavCommand.BinaryName} with elevated permissions :)")
                return null
            }
            else -> {
                terminal.danger("An unknown error occurred: $msg")
                terminal.info("If this should be considered a bug, please report it.")
                return null
            }
        }
    }
}
