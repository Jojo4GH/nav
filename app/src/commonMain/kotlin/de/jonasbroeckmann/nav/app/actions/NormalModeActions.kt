package de.jonasbroeckmann.nav.app.actions

import de.jonasbroeckmann.nav.app.*
import de.jonasbroeckmann.nav.app.macros.computeCondition
import de.jonasbroeckmann.nav.app.macros.computeDescription
import de.jonasbroeckmann.nav.app.state.Entry.Type.Directory
import de.jonasbroeckmann.nav.app.state.Entry.Type.RegularFile
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.framework.action.KeyAction
import de.jonasbroeckmann.nav.framework.action.KeyActions
import de.jonasbroeckmann.nav.framework.semantics.autocomplete
import de.jonasbroeckmann.nav.framework.semantics.updateTextField
import de.jonasbroeckmann.nav.utils.WorkingDirectory

@Suppress("unused")
class NormalModeActions(context: FullContext) : KeyActions<State, MainController, Unit>(), FullContext by context {
    val menuSubmit = registerKeyAction(
        config.keys.submit,
        condition = { isMenuOpen },
        action = { currentMenuAction?.run(null) }
    )

    val normalModeMacroActions = config.macros.mapNotNull { macro ->
        if (macro.nonQuickModeKey == null) return@mapNotNull null
        registerKeyAction(
            macro.nonQuickModeKey,
            description = { macro.computeDescription() },
            style = { macro.style },
            hidden = { macro.hidden },
            condition = { macro.computeCondition() },
            action = { runMacro(macro) }
        )
    }

    val cursorUp = registerKeyAction(
        config.keys.cursor.up,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorShifted(-1) } }
    )
    val cursorDown = registerKeyAction(
        config.keys.cursor.down,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorShifted(+1) } }
    )
    val cursorHome = registerKeyAction(
        config.keys.cursor.home,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorCoerced(0) } }
    )
    val cursorEnd = registerKeyAction(
        config.keys.cursor.end,
        condition = { filteredItems.isNotEmpty() },
        action = { updateState { withCursorCoerced(filteredItems.lastIndex) } }
    )

    val navigateUp = registerKeyAction(
        config.keys.nav.up,
        condition = { directory.parent != null },
        action = { updateState { navigatedUp() } }
    )
    val navigateInto = registerKeyAction(
        config.keys.nav.into,
        condition = { currentItem?.type == Directory || currentItem?.linkTarget?.targetEntry?.type == Directory },
        action = { updateState { navigateTo(currentItem?.path) } }
    )
    val navigateOpen = registerKeyAction(
        config.keys.nav.open,
        description = { "open in ${editorCommand ?: "editor"}" },
        style = { styles.file },
        condition = { currentItem?.type == RegularFile || currentItem?.linkTarget?.targetEntry?.type == RegularFile },
        action = { openInEditor(currentItem?.path ?: throw IllegalStateException("Cannot open file")) }
    )

    val discardCommand = registerKeyAction(
        config.keys.cancel,
        description = { "discard command" },
        condition = { isTypingCommand },
        action = { updateState { withCommand(null) } }
    )

    val autocompleteFilter = registerKeyAction(
        config.keys.filter.autocomplete, config.keys.filter.autocomplete.copy(shift = true),
        description = { "autocomplete" },
        condition = { unfilteredItems.isNotEmpty() },
        action = {
            autocomplete(
                autocompleteOn = { path.name },
                style = config.autocomplete.style.value,
                autoNavigation = config.autocomplete.autoNavigation.value,
                invertDirection = it.shift,
                onUpdate = { newState -> updateState { newState } },
                onAutoNavigate = { newState, item -> updateState { newState.navigateTo(item.path) } }
            )
        }
    )
    val clearFilter = registerKeyAction(
        config.keys.filter.clear,
        description = { "clear filter" },
        condition = { filter.isNotEmpty() },
        action = { updateState { withFilter("") } }
    )

    val exitMenu = registerKeyAction(
        config.keys.cancel,
        description = { "close menu" },
        condition = { isMenuOpen },
        action = { updateState { withMenuCursorCoerced(-1) } }
    )
    val closeMenu = registerKeyAction(
        config.keys.menu.up,
        description = { "close menu" },
        condition = { isMenuOpen && coercedMenuCursor == 0 },
        action = { updateState { withMenuCursorCoerced(-1) } }
    )
    val openMenu = registerKeyAction(
        config.keys.menu.down,
        description = { "more" },
        condition = { !isMenuOpen },
        action = { updateState { withMenuCursorCoerced(0) } }
    )
    val menuDown = registerKeyAction(
        config.keys.menu.down,
        condition = { isMenuOpen && coercedMenuCursor < shownMenuActions.lastIndex },
        action = { updateState { withMenuCursorCoerced(coercedMenuCursor + 1) } }
    )
    val menuUp = registerKeyAction(
        config.keys.menu.up,
        condition = { isMenuOpen && coercedMenuCursor > 0 },
        action = { updateState { withMenuCursorCoerced(coercedMenuCursor - 1) } }
    )

    val exitCD = registerKeyAction(
        config.keys.submit,
        description = { "exit here" },
        style = { styles.path },
        condition = { directory != WorkingDirectory },
        action = { exit(directory) }
    )
    val exit = registerKeyAction(
        config.keys.cancel,
        description = { "exit" },
        condition = { true },
        action = { exit(null) }
    )

    val inputCommand = registerKeyAction(
        KeyAction(
            keys = null,
            hidden = { true },
            condition = { isTypingCommand },
            action = { input ->
                input.updateTextField(
                    current = command ?: "",
                    onChange = { newCommand -> updateState { withCommand(newCommand) } }
                )
            }
        )
    )
    val inputFilter = registerKeyAction(
        KeyAction(
            keys = null,
            hidden = { true },
            condition = { true },
            action = { input ->
                input.updateTextField(
                    current = filter,
                    onChange = { newFilter -> updateState { withFilter(newFilter) } }
                )
            }
        )
    )

    val all = registered[Unit].orEmpty()
}
