package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.utils.Paths
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator

sealed class DefaultMacroProperty(
    val property: MacroProperty<MacroValue.Text?>
) {
    // From context

    object WorkingDirectory : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "workingDirectory",
            onGetString = { Paths.WorkingDirectory.toString() }
        )
    )

    object StartingDirectory : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "startingDirectory",
            onGetString = { context.startingDirectory.toString() }
        )
    )

    object DebugMode : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "debugMode",
            onGetString = { context.debugMode.toString() }
        )
    )

    object Shell : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "shell",
            onGetString = { context.shell?.shell }
        )
    )

    object Separator : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "separator",
            onGetString = { "$RealSystemPathSeparator" }
        )
    )

    // From state

    object Directory : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            name = "directory",
            onGetString = { state.directory.toString() },
            onSetString = { newValue -> newValue.parseToAbsolutePathToDirectoryOrNull()?.let { updateState { navigatedTo(it) } } }
        )
    )

    object EntryPath : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "entryPath",
            onGetString = { state.currentItem?.path?.toString() }
        )
    )

    object EntryName : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "entryName",
            onGetString = { state.currentItem?.path?.name }
        )
    )

    object EntryType : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "entryType",
            onGetString = {
                when (state.currentItem?.type) {
                    Entry.Type.Directory -> Value.DIRECTORY
                    Entry.Type.RegularFile -> Value.FILE
                    Entry.Type.SymbolicLink -> Value.LINK
                    Entry.Type.Unknown -> Value.UNKNOWN
                    null -> null
                }
            }
        )
    ) {
        object Value {
            const val DIRECTORY = "directory"
            const val FILE = "file"
            const val LINK = "link"
            const val UNKNOWN = "unknown"
        }
    }

    object Filter : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            name = "filter",
            onGetString = { state.filter },
            onSetString = { newValue -> updateState { withFilter(newValue.orEmpty()) } }
        )
    )

    object FilteredEntriesCount : DefaultMacroProperty(
        MacroProperty.DelegatedImmutable(
            name = "filteredEntriesCount",
            onGetString = { state.filteredItems.size.toString() }
        )
    )

    object Command : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            name = "command",
            onGetString = { state.command },
            onSetString = { newValue -> updateState { withCommand(newValue?.takeUnless { it.isEmpty() }) } }
        )
    )

    object EntryCursorPosition : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            name = "entryCursorPosition",
            onGetString = { state.cursor.toString() },
            onSetString = { newValue -> newValue?.toIntOrNull()?.let { updateState { withCursor(it) } } }
        )
    )

    object MenuCursorPosition : DefaultMacroProperty(
        MacroProperty.DelegatedMutable(
            name = "menuCursorPosition",
            onGetString = { state.menuCursor.toString() },
            onSetString = { newValue -> newValue?.toIntOrNull()?.let { updateState { withMenuCursor(it) } } }
        )
    )

    val name get() = property.name

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
        val ByName by lazy {
            All.associate { it.name to it.property }
        }
    }
}
