package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.App.Event.*


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
