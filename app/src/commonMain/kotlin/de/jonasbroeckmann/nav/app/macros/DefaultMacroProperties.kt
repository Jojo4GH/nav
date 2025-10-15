package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.updateState

enum class DefaultMacroProperties(
    val property: MacroProperty
) {
    // From context
    WorkingDirectory(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("workingDirectory"),
            onGet = { de.jonasbroeckmann.nav.utils.WorkingDirectory.toString() }
        )
    ),
    StartingDirectory(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("startingDirectory"),
            onGet = { context.startingDirectory.toString() }
        )
    ),
    DebugMode(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("debugMode"),
            onGet = { context.debugMode.toString() }
        )
    ),
    Shell(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("shell"),
            onGet = { context.shell?.shell }
        )
    ),

    // From state
    Directory(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("directory"),
            onGet = { state.directory.toString() },
            onSet = { newValue -> newValue.parseAbsolutePathToDirectoryOrNull()?.let { updateState { navigateTo(it) } } }
        )
    ),
    EntryPath(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryPath"),
            onGet = { state.currentItem?.path?.toString() }
        )
    ),
    EntryName(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryName"),
            onGet = { state.currentItem?.path?.name }
        )
    ),
    EntryType(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryType"),
            onGet = {
                when (state.currentItem?.type) {
                    Entry.Type.Directory -> "directory"
                    Entry.Type.RegularFile -> "file"
                    Entry.Type.SymbolicLink -> "link"
                    Entry.Type.Unknown -> "unknown"
                    null -> null
                }
            }
        )
    ),
    Filter(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("filter"),
            onGet = { state.filter },
            onSet = { newValue -> updateState { withFilter(newValue) } }
        )
    ),
    FilteredEntriesCount(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("filteredEntriesCount"),
            onGet = { state.filteredItems.size.toString() }
        )
    ),
    Command(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("command"),
            onGet = { state.command },
            onSet = { newValue -> updateState { withCommand(newValue.takeUnless { it.isEmpty() }) } }
        )
    ),
    EntryCursorPosition(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("entryCursorPosition"),
            onGet = { state.cursor.toString() },
            onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withCursorCoerced(it) } } }
        )
    ),
    MenuCursorPosition(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("menuCursorPosition"),
            onGet = { state.coercedMenuCursor.toString() },
            onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withMenuCursorCoerced(it) } } }
        )
    );

    companion object {
        val BySymbol by lazy {
            entries.associate { it.property.symbol to it.property }
        }
    }
}
