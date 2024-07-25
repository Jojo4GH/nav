package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.platform.MultiplatformSystem.exitProcess
import com.github.ajalt.mordant.platform.MultiplatformSystem.readEnvironmentVariable
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.writeString

private val terminal = Terminal()

private val workingDirectory: Path = SystemFileSystem.resolve(Path("."))

private val userHome: Path = (getenv("HOME") ?: getenv("USERPROFILE"))
    ?.let { SystemFileSystem.resolve(Path(it)) }
    ?: throw IllegalStateException("Could not determine user home directory")
private val navFile = userHome / ".nav-cd"

fun main() {

    val selection = SelectInputAnimation(
        terminal,
        Config(
            startingDirectory = workingDirectory
//            entries = mutableListOf(
//                SelectList.Entry(".git"),
//                SelectList.Entry(".gradle"),
//                SelectList.Entry(".idea"),
//                SelectList.Entry(".kotlin"),
//                SelectList.Entry("app"),
//                SelectList.Entry("gradle"),
//                SelectList.Entry(".gitignore"),
//                SelectList.Entry("gradle.properties"),
//                SelectList.Entry("gradlew"),
//                SelectList.Entry("gradlew.bat"),
//                SelectList.Entry("settings.gradle.kts"),
//            )
        )
    ).receiveEvents()
    if (selection == null) {
        terminal.danger("Nothing")
    } else {
        terminal.success("Selected: $selection")
        val metadata = SystemFileSystem.metadataOrNull(selection)
        if (metadata?.isDirectory == true) {
            broadcastChangeDirectory(selection)
        } else if (metadata?.isRegularFile == true) {
            terminal.info("Opening file with nano: $selection")
        }
    }
}

fun broadcastChangeDirectory(path: Path) {
    if (SystemFileSystem.exists(navFile)) {
        terminal.danger("$navFile already exists")
        return
    }
    SystemFileSystem.sink(navFile).buffered().use {
        it.writeString(path.toString())
    }
}

expect fun getenv(key: String): String?

expect fun changeDirectory(path: Path): Boolean


private data class Config(
    val startingDirectory: Path,

    val limit: Int = 12,
    val startingCursorIndex: Int = 0,
    val onlyShowActiveDescription: Boolean = false,
    val clearOnExit: Boolean = true,

    val cursorMarker: String? = null,
    val selectedStyle: TextStyle? = null,
    val unselectedTitleStyle: TextStyle? = null,

    val keyNext: KeyboardEvent = KeyboardEvent("ArrowDown"),
    val keyPrev: KeyboardEvent = KeyboardEvent("ArrowUp"),
    val keyNavUp: KeyboardEvent = KeyboardEvent("ArrowLeft"),
    val keyNavDown: KeyboardEvent = KeyboardEvent("ArrowRight"),

    val keySubmit: KeyboardEvent = KeyboardEvent("Enter"),

    val filterable: Boolean = true,
    val keyAutocompleteFilter: KeyboardEvent = KeyboardEvent("Tab"),
    val keyClearFilter: KeyboardEvent = KeyboardEvent("Escape"),

    val showInstructions: Boolean = true,
)

data class Entry(
    val path: Path,
    private val metadata: FileMetadata?
) {
    val isDirectory get() = metadata?.isDirectory == true
    val isRegularFile get() = metadata?.isRegularFile == true
    val size get() = metadata?.size?.takeIf { it >= 0 }
}

private class SelectInputAnimation(
    override val terminal: Terminal,
    private val config: Config
) : InputReceiverAnimation<Path?> {


    private data class State(
        val directory: Path,
        val items: List<Entry> = directory.entries(),
        val cursor: Int,
        val filter: String = "",
        val finished: Boolean = false,
    ) {
        val filteredItems: List<Entry> by lazy {
            if (filter.isEmpty()) items else items.filter {
                filter.lowercase() in it.path.name.lowercase()
            }
        }
        val currentEntry: Entry? get() = filteredItems.getOrNull(cursor)

        fun withCursor(cursor: Int) = copy(
            cursor = when {
                filteredItems.isEmpty() -> 0
                else -> cursor.mod(filteredItems.size)
            }
        )

        fun filtered(filter: String): State {
            val tmp = copy(filter = filter)
            return tmp.copy(items = tmp.items, cursor = tmp.cursor.coerceAtMost(tmp.filteredItems.lastIndex).coerceAtLeast(0))
        }

        fun navigatedUp(): State {
            val newDir = directory.parent ?: return this
            val entries = newDir.entries()
            return State(
                directory = newDir,
                items = entries,
                cursor = entries.indexOfFirst { it.path.name == directory.name }.coerceAtLeast(0)
            )
        }

        fun navigatedInto(entry: Entry): State {
            if (!entry.isDirectory) return this
            return State(
                directory = entry.path,
                cursor = 0
            )
        }

        companion object {
            private fun Path.entries(): List<Entry> = SystemFileSystem.list(this).map {
                Entry(it, SystemFileSystem.metadataOrNull(it))
            }
        }
    }

    private var state = State(directory = config.startingDirectory, cursor = config.startingCursorIndex)

    private val animation = terminal.animation<State> { s ->
        with(config) {
//            SelectList(
//                entries = when {
//                    onlyShowActiveDescription -> s.filteredItems.mapIndexed { i, entry ->
//                        entry.copy(description = if (i == s.cursor) entry.description else null)
//                    }
//                    else -> s.filteredItems
//                },
//                title = when {
//                    s.filter.isNotEmpty() -> Text("${s.directory} ${terminal.theme.style("select.cursor")("$SystemPathSeparator")} ${s.filter}")
//                    else -> Text("${s.directory}")
//                },
//                cursorIndex = s.cursor,
//                styleOnHover = true,
//                cursorMarker = cursorMarker,
//                selectedStyle = selectedStyle,
//                unselectedTitleStyle = unselectedTitleStyle,
//
//                captionBottom = if (showInstructions) Text(buildInstructions(s.filter.isNotEmpty())) else null,
//            )


            val entries = s.filteredItems
            val selectedIndex = s.cursor

            val filterPrompt = terminal.theme.style("select.cursor")("$SystemPathSeparator")
            val titleStyle = TextStyle(TextColors.brightGreen)
            val filterStyle = TextStyle(TextColors.brightWhite)
            val title = Text(when {
                s.filter.isNotEmpty() -> "${titleStyle("${s.directory}")} $filterPrompt ${filterStyle(s.filter)}"
                else -> titleStyle("${s.directory}")
            })

            val bottom = if (showInstructions) Text(buildInstructions(
                directory = s.directory,
                currentEntry = s.currentEntry,
                hasFilter = s.filter.isNotEmpty()
            )) else null

            val dirStyle = TextStyle(TextColors.brightBlue)
            val fileStyle = TextStyle(TextColors.brightWhite)
            val sizeStyle = TextStyle(TextColors.brightGreen, dim = true)

            val highlighted = TextStyle(inverse = true)

            table {
                captionTop(title)
                if (bottom != null) captionBottom(bottom)

                cellBorders = Borders.LEFT_RIGHT
                tableBorders = Borders.NONE
                borderType = BorderType.BLANK
                padding = Padding(0)
                whitespace = Whitespace.PRE_WRAP

                body {
                    // TODO calculate limited view of entries
                    for ((i, entry) in entries.withIndex()) {
                        row {
//                            if (selectedIndex != null && cursorBlank.isNotEmpty()) {
//                                cell(if (i == selectedIndex) cursor else cursorBlank)
//                            }
//                            if (selectedMarker[t].isNotEmpty()) {
//                                cell(if (entry.selected) styledSelectedMarker else styledUnselectedMarker)
//                            }

                            cell(sizeStyle(entry.size?.toString() ?: ""))

                            val name = when {
                                i == selectedIndex -> highlighted(entry.path.name)
                                else -> entry.path.name
                            }
                            cell(when {
                                entry.isDirectory -> "${dirStyle(name)}/"
                                entry.isRegularFile -> fileStyle(name)
                                else -> name
                            })
                        }
                    }
                }
            }
        }
    }.apply { update(state) }

    override fun stop() = animation.stop()
    override fun clear() = animation.clear()

    override fun receiveEvent(event: InputEvent): Status<Path?> {
        if (event !is KeyboardEvent) return Status.Continue
        val current = state.currentEntry

        var result: Path? = null

        state = with(config) {
            when {
                // Interrupt
                event.isCtrlC -> {
                    result = workingDirectory
                    state
                }
                // Move cursor
                event == keyPrev -> state.withCursor(state.cursor - 1)
                event == keyNext -> state.withCursor(state.cursor + 1)
                event == KeyboardEvent("Home") -> state.withCursor(0)
                event == KeyboardEvent("End") -> state.withCursor(state.filteredItems.lastIndex)
                // Tree navigation
                event == keyNavUp && state.directory.parent != null -> state.navigatedUp()
                event == keyNavDown && current?.isDirectory == true -> state.navigatedInto(current)
                // Open file
                event == keyNavDown && current?.isRegularFile == true -> {
                    result = current.path
                    state
                }
                // Autocomplete filter
                event == keyAutocompleteFilter && state.items.isNotEmpty() -> {
                    val prefix = state.items
                        .map { it.path.name.lowercase() }
                        .filter { it.startsWith(state.filter.lowercase()) }
                        .commonPrefix()
                    state.filtered(prefix)
                }
                // Clear filter
                event == keyClearFilter -> state.filtered("")
                // Submit
                event == keySubmit -> {
                    result = state.directory
                    state
                }
                // Filter
                !event.alt && !event.ctrl -> when {
                    event == KeyboardEvent("Backspace") -> state.filtered(state.filter.dropLast(1))
                    event.key.length == 1 -> state.filtered(state.filter + event.key)
                    else -> state // ignore modifier keys
                }
                // Unhandled
                else -> state // unmapped key, no state change
            }
        }
        animation.update(state)
        return when {
            result != null -> {
                if (config.clearOnExit) animation.clear()
                else animation.stop()
                if (event.isCtrlC) Status.Finished(null)
                else Status.Finished(result.takeUnless { it == workingDirectory })
            }
            else -> Status.Continue
        }
    }

    private fun keyName(key: KeyboardEvent): String {
        var k = when (key.key) {
            "Enter" -> "enter"
            "Escape" -> "esc"
            "Tab" -> "tab"
            "ArrowUp" -> "↑"
            "ArrowDown" -> "↓"
            "ArrowLeft" -> "←"
            "ArrowRight" -> "→"
            else -> key.key
        }
        if (key.alt) k = "alt+$k"
        if (key.ctrl) k = "ctrl+$k"
        if (key.shift && key.key.length > 1) k = "shift+$k"
        return k
    }

    private fun buildInstructions(
        directory: Path,
        currentEntry: Entry?,
        hasFilter: Boolean,
    ): String = with(config) {
        val styleKey = TextStyle(TextColors.brightWhite, bold = true)
        val styleDesc = TextStyles.dim
        return buildList {
            if (directory.parent != null) add(styleKey(keyName(keyNavUp)))
            add("${styleKey(keyName(keyPrev))} ${styleKey(keyName(keyNext))}")
            if (currentEntry?.isDirectory == true) add(styleKey(keyName(keyNavDown)))
            else if (currentEntry?.isRegularFile == true) add("${styleKey(keyName(keyNavDown))} ${styleDesc("open file")}")

            if (hasFilter) {
                add("${styleKey(keyName(keyAutocompleteFilter))} ${styleDesc("autocomplete")}")
                add("${styleKey(keyName(keyClearFilter))} ${styleDesc("clear filter")}")
            }

            if (directory != workingDirectory) add("${styleKey(keyName(keySubmit))} ${styleDesc("cd & exit")}")
            else add("${styleKey(keyName(keySubmit))} ${styleDesc("exit")}")

        }.joinToString(TextStyles.dim(" • "))
    }
}

fun Iterable<String>.commonPrefix(): String {
    val iter = iterator()
    if (!iter.hasNext()) return ""
    var prefix = iter.next()
    while (iter.hasNext()) {
        val next = iter.next()
        prefix = prefix.commonPrefixWith(next)
    }
    return prefix
}


operator fun Path.div(child: String) = Path(this, child)



expect val platformName: String
