package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.context
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.parseToAbsolutePathToDirectoryOrNull
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.state
import de.jonasbroeckmann.nav.app.updateState
import de.jonasbroeckmann.nav.utils.Paths
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import kotlin.collections.get

sealed class KnownMacroProperty : MacroProperty<MacroValue.Text> {
    // From context

    object WorkingDirectory : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "workingDirectory",
        onGetString = { Paths.WorkingDirectory.toString() }
    )

    object StartingDirectory : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "startingDirectory",
        onGetString = { context.startingDirectory.toString() }
    )

    object DebugMode : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "debugMode",
        onGetString = { context.debugMode.toString() }
    )

    object Shell : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "shell",
        onGetString = { context.shell?.shell.orEmpty() }
    )

    object Separator : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "separator",
        onGetString = { "$RealSystemPathSeparator" }
    )

    // From state

    object Directory : KnownMacroProperty(), MacroProperty.Mutable<MacroValue.Text> by MacroProperty.delegatedString(
        name = "directory",
        onGetString = { state.directory.toString() },
        onSetString = { newValue -> newValue.parseToAbsolutePathToDirectoryOrNull()?.let { updateState { navigatedTo(it) } } }
    )

    object EntryPath : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "entryPath",
        onGetString = { state.currentItem?.path?.toString().orEmpty() }
    )

    object EntryName : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "entryName",
        onGetString = { state.currentItem?.path?.name.orEmpty() }
    )

    object EntryType : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "entryType",
        onGetString = {
            when (state.currentItem?.type) {
                Entry.Type.Directory -> Value.DIRECTORY
                Entry.Type.RegularFile -> Value.FILE
                Entry.Type.SymbolicLink -> Value.LINK
                Entry.Type.Unknown -> Value.UNKNOWN
                null -> null.orEmpty()
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

    object Filter : KnownMacroProperty(), MacroProperty.Mutable<MacroValue.Text> by MacroProperty.delegatedString(
        name = "filter",
        onGetString = { state.filter },
        onSetString = { newValue -> updateState { withFilter(newValue) } }
    )

    object FilteredEntriesCount : KnownMacroProperty(), MacroProperty<MacroValue.Text> by MacroProperty.delegatedString(
        name = "filteredEntriesCount",
        onGetString = { state.filteredItems.size.toString() }
    )

    object Command : KnownMacroProperty(), MacroProperty.Mutable<MacroValue.Text> by MacroProperty.delegatedString(
        name = "command",
        onGetString = { state.command.orEmpty() },
        onSetString = { newValue -> updateState { withCommand(newValue.takeUnless { it.isEmpty() }) } }
    )

    object EntryCursorPosition : KnownMacroProperty(), MacroProperty.Mutable<MacroValue.Text> by MacroProperty.delegatedString(
        name = "entryCursorPosition",
        onGetString = { state.cursor.toString() },
        onSetString = { newValue -> newValue.toIntOrNull()?.let { updateState { withCursor(it) } } }
    )

    object MenuCursorPosition : KnownMacroProperty(), MacroProperty.Mutable<MacroValue.Text> by MacroProperty.delegatedString(
        name = "menuCursorPosition",
        onGetString = { state.menuCursor.toString() },
        onSetString = { newValue -> newValue.toIntOrNull()?.let { updateState { withMenuCursor(it) } } }
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

        fun from(expression: MacroExpression): KnownMacroProperty? {
            if (expression.storageType !is Property?) return null
            val name = (expression.path.operators.singleOrNull() as? MacroPathExpression.Operator.Key)?.key
            return ByName[name]
        }
    }
}
