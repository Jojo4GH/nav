package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.AppAction
import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.State

enum class DefaultMacroVariables(
    val label: String,
    val variable: () -> MacroVariable = { MacroVariable.Custom(label) }
) {
    // From context
    WorkingDirectory(
        "workingDirectory",
        {
            MacroVariable.DelegatedImmutable(
                name = "workingDirectory",
                onGet = { de.jonasbroeckmann.nav.utils.WorkingDirectory.toString() }
            )
        }
    ),
    StartingDirectory(
        "startingDirectory",
        {
            MacroVariable.DelegatedImmutable(
                name = "startingDirectory",
                onGet = { context.startingDirectory.toString() }
            )
        }
    ),
    DebugMode(
        "debugMode",
        {
            MacroVariable.DelegatedImmutable(
                name = "debugMode",
                onGet = { context.debugMode.toString() }
            )
        }
    ),
    Shell(
        "shell",
        {
            MacroVariable.DelegatedImmutable(
                name = "shell",
                onGet = { context.shell?.shell }
            )
        }
    ),

    // From state
    Directory(
        "directory",
        {
            MacroVariable.DelegatedMutable(
                name = "directory",
                onGet = { state.directory.toString() },
                onSet = { newValue -> newValue.parsePathToDirectoryOrNull()?.let { updateState { navigateTo(it) } } }
            )
        }
    ),
    EntryPath(
        "entryPath",
        {
            MacroVariable.DelegatedImmutable(
                name = "entryPath",
                onGet = { state.currentEntry?.path?.toString() }
            )
        }
    ),
    EntryName(
        "entryName",
        {
            MacroVariable.DelegatedImmutable(
                name = "entryName",
                onGet = { state.currentEntry?.path?.name }
            )
        }
    ),
    EntryType(
        "entryType",
        {
            MacroVariable.DelegatedImmutable(
                name = "entryType",
                onGet = {
                    when (state.currentEntry?.type) {
                        Entry.Type.Directory -> "directory"
                        Entry.Type.RegularFile -> "file"
                        Entry.Type.SymbolicLink -> "link"
                        Entry.Type.Unknown -> "unknown"
                        null -> null
                    }
                }
            )
        }
    ),
    Filter(
        "filter",
        {
            MacroVariable.DelegatedMutable(
                name = "filter",
                onGet = { state.filter },
                onSet = { newValue -> updateState { withFilter(newValue) } }
            )
        }
    ),
    FilteredEntriesCount(
        "filteredEntriesCount",
        {
            MacroVariable.DelegatedImmutable(
                name = "filteredEntriesCount",
                onGet = { state.filteredItems.size.toString() }
            )
        }
    ),
    Command(
        "command",
        {
            MacroVariable.DelegatedMutable(
                name = "command",
                onGet = { state.command },
                onSet = { newValue -> updateState { withCommand(newValue.takeUnless { it.isEmpty() }) } }
            )
        }
    ),
    EntryCursorPosition(
        "entryCursorPosition",
        {
            MacroVariable.DelegatedMutable(
                name = "entryCursorPosition",
                onGet = { state.cursor.toString() },
                onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withCursorCoerced(it) } } }
            )
        }
    ),
    MenuCursorPosition(
        "menuCursorPosition",
        {
            MacroVariable.DelegatedMutable(
                name = "menuCursorPosition",
                onGet = { state.coercedMenuCursor.toString() },
                onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withMenuCursorCoerced(it) } } }
            )
        }
    ),

    // Local
    ExitCode("exitCode");

    val placeholder by lazy { StringWithPlaceholders.placeholder(label) }

    companion object {
        val ByName = entries.associateBy { it.label }

        context(context: MacroRuntimeContext)
        private fun updateState(block: State.() -> State) = context.run(AppAction.UpdateState(block))
    }
}
