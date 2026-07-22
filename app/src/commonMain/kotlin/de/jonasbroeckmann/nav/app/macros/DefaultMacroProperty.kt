package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator

sealed class DefaultMacroProperty(
    val property: MacroProperty
) {
    // From context

    object WorkingDirectory : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("workingDirectory"),
            onGet = { de.jonasbroeckmann.nav.utils.WorkingDirectory.toString() }
        )
    )

    object StartingDirectory : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("startingDirectory"),
            onGet = { context.startingDirectory.toString() }
        )
    )

    object DebugMode : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("debugMode"),
            onGet = { context.debugMode.toString() }
        )
    )

    object Shell : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("shell"),
            onGet = { context.shell?.shell }
        )
    )

    object Separator : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("separator"),
            onGet = { "$RealSystemPathSeparator" }
        )
    )

    // From state

    object Directory : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("directory"),
            onGet = { state.directory.toString() },
            onSet = { newValue -> newValue.parseAbsolutePathToDirectoryOrNull()?.let { updateState { navigatedTo(it) } } }
        )
    )

    object EntryPath : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryPath"),
            onGet = { state.currentItem?.path?.toString() }
        )
    )

    object EntryName : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryName"),
            onGet = { state.currentItem?.path?.name }
        )
    )

    object EntryType : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("entryType"),
            onGet = {
                when (state.currentItem?.type) {
                    Entry.Type.Directory -> Value.Directory
                    Entry.Type.RegularFile -> Value.File
                    Entry.Type.SymbolicLink -> Value.Link
                    Entry.Type.Unknown -> Value.Unknown
                    null -> null
                }
            }
        )
    ) {
        object Value {
            const val Directory = "directory"
            const val File = "file"
            const val Link = "link"
            const val Unknown = "unknown"
        }
    }

    object Filter : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("filter"),
            onGet = { state.filter },
            onSet = { newValue -> updateState { withFilter(newValue) } }
        )
    )

    object FilteredEntriesCount : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            symbol = MacroSymbol.Generic("filteredEntriesCount"),
            onGet = { state.filteredItems.size.toString() }
        )
    )

    object Command : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("command"),
            onGet = { state.command },
            onSet = { newValue -> updateState { withCommand(newValue.takeUnless { it.isEmpty() }) } }
        )
    )

    object EntryCursorPosition : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("entryCursorPosition"),
            onGet = { state.cursor.toString() },
            onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withCursor(it) } } }
        )
    )

    object MenuCursorPosition : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            symbol = MacroSymbol.Generic("menuCursorPosition"),
            onGet = { state.menuCursor.toString() },
            onSet = { newValue -> newValue.toIntOrNull()?.let { updateState { withMenuCursor(it) } } }
        )
    )

    val symbol get() = property.symbol

    val placeholder get() = symbol.placeholder

    override fun toString() = placeholder.toString()

    companion object {
        val All = listOf(
            WorkingDirectory,
            StartingDirectory,
            DebugMode,
            Shell,
            Separator,
            Directory,
            EntryPath,
            EntryName,
            EntryType,
            Filter,
            FilteredEntriesCount,
            Command,
            EntryCursorPosition,
            MenuCursorPosition,
        )
        val BySymbol by lazy {
            All.associate { it.symbol to it.property }
        }
    }
}
