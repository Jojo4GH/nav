package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.NavCommand
import de.jonasbroeckmann.nav.app.App.Event.*
import de.jonasbroeckmann.nav.utils.WorkingDirectory
import de.jonasbroeckmann.nav.utils.commonPrefix
import kotlinx.io.IOException


class Actions(config: Config) {
    val cursorUp = KeyAction(
        key = config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(cursor - 1)) }
    )
    val cursorDown = KeyAction(
        key = config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(cursor + 1)) }
    )
    val cursorHome = KeyAction(
        key = config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(0)) }
    )
    val cursorEnd = KeyAction(
        key = config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { NewState(withCursor(filteredItems.lastIndex)) }
    )

    val navigateUp = KeyAction(
        key = config.keys.nav.up,
        condition = { directory.parent != null },
        action = { NewState(navigatedUp()) }
    )
    val navigateInto = KeyAction(
        key = config.keys.nav.into,
        condition = { currentEntry?.isDirectory == true },
        action = { NewState(navigatedInto(currentEntry)) }
    )
    val navigateOpen = KeyAction(
        key = config.keys.nav.open,
        description = "open in ${config.editor}",
        style = TextColors.rgb(config.colors.file),
        condition = { currentEntry?.isRegularFile == true },
        action = { OpenFile(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
    )

    val exitCD = KeyAction(
        key = config.keys.submit,
        description = "exit here",
        style = TextColors.rgb(config.colors.path),
        condition = { directory != WorkingDirectory && filter.isEmpty() },
        action = { ExitAt(directory) }
    )
    val exit = KeyAction(
        key = config.keys.cancel,
        description = "cancel",
        condition = { filter.isEmpty() },
        action = { Exit }
    )

    val autocompleteFilter = KeyAction(
        key = config.keys.filter.autocomplete,
        description = "autocomplete",
        condition = { filter.isNotEmpty() && items.isNotEmpty() },
        action = {
            val commonPrefix = items
                .map { it.path.name.lowercase() }
                .filter { it.startsWith(filter.lowercase()) }
                .commonPrefix()
            NewState(filtered(commonPrefix))
        }
    )
    val clearFilter = KeyAction(
        key = config.keys.filter.clear,
        description = "clear filter",
        condition = { filter.isNotEmpty() },
        action = { NewState(filtered("")) }
    )

    val ordered = listOf(
        cursorUp, cursorDown, cursorHome, cursorEnd,
        navigateUp, navigateInto, navigateOpen,
        exitCD, exit,
        autocompleteFilter, clearFilter
    )
}

data class KeyAction(
    val key: KeyboardEvent,
    val description: String? = null,
    val style: TextStyle? = null,
    private val condition: State.() -> Boolean,
    val action: State.(KeyboardEvent) -> App.Event
) {
    fun matches(event: KeyboardEvent, state: State) = key == event && available(state)
    fun available(state: State) = state.condition()
}

fun KeyAction.tryAction(event: KeyboardEvent, state: State, terminal: Terminal): App.Event? {
    try {
        return state.action(event)
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
