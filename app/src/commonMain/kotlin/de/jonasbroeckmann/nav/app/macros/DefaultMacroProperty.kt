package de.jonasbroeckmann.nav.app.macros

import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.macros.MacroPathExpression.Operator
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.utils.Paths
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import kotlin.collections.get

// TODO rename to KnownMacroProperty
sealed class DefaultMacroProperty : MacroProperty<MacroValue.Text?> {
    // From context

    object WorkingDirectory : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "workingDirectory",
        onGetString = { Paths.WorkingDirectory.toString() }
    )

    object StartingDirectory : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "startingDirectory",
        onGetString = { context.startingDirectory.toString() }
    )

    object DebugMode : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "debugMode",
        onGetString = { context.debugMode.toString() }
    )

    object Shell : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "shell",
        onGetString = { context.shell?.shell }
    )

    object Separator : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "separator",
        onGetString = { "$RealSystemPathSeparator" }
    )

    // From state

    object Directory : DefaultMacroProperty(), MacroProperty.Mutable<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "directory",
        onGetString = { state.directory.toString() },
        onSetString = { newValue -> newValue?.parseToAbsolutePathToDirectoryOrNull()?.let { updateState { navigatedTo(it) } } }
    )

    object EntryPath : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "entryPath",
        onGetString = { state.currentItem?.path?.toString() }
    )

    object EntryName : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "entryName",
        onGetString = { state.currentItem?.path?.name }
    )

    object EntryType : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
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
    ) {
        object Value {
            const val DIRECTORY = "directory"
            const val FILE = "file"
            const val LINK = "link"
            const val UNKNOWN = "unknown"
        }
    }

    object Filter : DefaultMacroProperty(), MacroProperty.Mutable<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "filter",
        onGetString = { state.filter },
        onSetString = { newValue -> updateState { withFilter(newValue.orEmpty()) } }
    )

    object FilteredEntriesCount : DefaultMacroProperty(), MacroProperty<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "filteredEntriesCount",
        onGetString = { state.filteredItems.size.toString() }
    )

    object Command : DefaultMacroProperty(), MacroProperty.Mutable<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "command",
        onGetString = { state.command },
        onSetString = { newValue -> updateState { withCommand(newValue?.takeUnless { it.isEmpty() }) } }
    )

    object EntryCursorPosition : DefaultMacroProperty(), MacroProperty.Mutable<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "entryCursorPosition",
        onGetString = { state.cursor.toString() },
        onSetString = { newValue -> newValue?.toIntOrNull()?.let { updateState { withCursor(it) } } }
    )

    object MenuCursorPosition : DefaultMacroProperty(), MacroProperty.Mutable<MacroValue.Text?> by MacroProperty.delegatedString(
        name = "menuCursorPosition",
        onGetString = { state.menuCursor.toString() },
        onSetString = { newValue -> newValue?.toIntOrNull()?.let { updateState { withMenuCursor(it) } } }
    )

    override fun toString() = templateString.toString()

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
            All.associateBy { it.name }
        }

        fun from(expression: MacroExpression): DefaultMacroProperty? {
            if (expression.storageType !is Property?) return null
            val name = (expression.path.operators.singleOrNull() as? Operator.Key)?.key
            return ByName[name]
        }
    }
}
