package de.jonasbroeckmann.nav

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.input.InputReceiver.Status
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.rendering.TextStyles.dim
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.SectionBuilder
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import kotlinx.io.writeString
import kotlin.time.Duration.Companion.days

private val terminal = Terminal()

private val workingDirectory: Path = SystemFileSystem.resolve(Path("."))

private val userHome: Path = (getenv("HOME") ?: getenv("USERPROFILE"))
    ?.let { SystemFileSystem.resolve(Path(it)) }
    ?: throw IllegalStateException("Could not determine user home directory")
private val navFile = userHome / ".nav-cd"


fun main() {

    val selection = SelectInputAnimation(
        terminal,
        Config(),
        startingDirectory = workingDirectory,
        startingCursorIndex = 0
    ).receiveEvents()
    if (selection == null) {
//        terminal.danger("Nothing")
    } else {
        val metadata = SystemFileSystem.metadataOrNull(selection)
        if (metadata?.isDirectory == true) {
            broadcastChangeDirectory(selection)
        } else if (metadata?.isRegularFile == true) {
            terminal.success("TODO Opening file with nano: $selection")
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
    val limit: Int = 32,
    val clearOnExit: Boolean = true,

    val cursorMarker: String? = null,
    val selectedStyle: TextStyle? = null,
    val unselectedTitleStyle: TextStyle? = null,

    val keyNext: KeyboardEvent = KeyboardEvent("ArrowDown"),
    val keyPrev: KeyboardEvent = KeyboardEvent("ArrowUp"),
    val keyNavUp: KeyboardEvent = KeyboardEvent("ArrowLeft"),
    val keyNavDown: KeyboardEvent = KeyboardEvent("ArrowRight"),

    val keySubmit: KeyboardEvent = KeyboardEvent("Enter"),

    val keyAutocompleteFilter: KeyboardEvent = KeyboardEvent("Tab"),
    val keyClearFilter: KeyboardEvent = KeyboardEvent("Escape"),

    val showInstructions: Boolean = true,
)

data class Entry(
    val path: Path,
    val stat: Stat
) {
    val isDirectory get() = stat.mode.isDirectory
    val isRegularFile get() = stat.mode.isRegularFile
    val isSymbolicLink get() = stat.mode.isSymbolicLink
    val size get() = stat.size.takeIf { it >= 0 && !isDirectory }
}



private class SelectInputAnimation(
    override val terminal: Terminal,
    private val config: Config,
    startingDirectory: Path,
    startingCursorIndex: Int
) : InputReceiverAnimation<Path?> {

    private data class State(
        val directory: Path,
        val items: List<Entry> = directory.entries(),
        val cursor: Int,
        val filter: String = "",
        val exit: Path? = null,
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

        fun navigatedInto(entry: Entry?): State {
            if (entry?.isDirectory != true) return this
            return State(
                directory = entry.path,
                cursor = 0
            )
        }

        fun exit(at: Path = workingDirectory) = copy(exit = at)

        companion object {
            private fun Path.entries(): List<Entry> = SystemFileSystem.list(this)
                .map { it.cleaned() } // fix broken paths
                .map { Entry(it, stat(it)) }
                .sortedBy { it.path.name }
                .sortedByDescending { it.isDirectory }
        }
    }


    private var state = State(directory = startingDirectory, cursor = startingCursorIndex)




    private val actions = Actions(config)

    private class Actions(
        config: Config
    ) {
        val cursorUp = KeyAction(
            key = config.keyPrev,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(cursor - 1) }
        )
        val cursorDown = KeyAction(
            key = config.keyNext,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(cursor + 1) }
        )
        val cursorHome = KeyAction(
            key = KeyboardEvent("Home"),
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(0) }
        )
        val cursorEnd = KeyAction(
            key = KeyboardEvent("End"),
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(filteredItems.lastIndex) }
        )

        val navigateUp = KeyAction(
            key = config.keyNavUp,
            condition = { directory.parent != null },
            action = { navigatedUp() }
        )
        val navigateInto = KeyAction(
            key = config.keyNavDown,
            condition = { currentEntry?.isDirectory == true },
            action = { navigatedInto(currentEntry) }
        )
        val navigateOpen = KeyAction(
            key = config.keyNavDown,
            description = "open file",
            condition = { currentEntry?.isRegularFile == true },
            action = { exit(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
        )

        val exitCD = KeyAction(
            key = config.keySubmit,
            description = "cd & exit",
            style = TextColors.rgb("1dff7b"),
            condition = { directory != workingDirectory && filter.isEmpty() },
            action = { exit(directory) }
        )
        val exit = KeyAction(
            key = KeyboardEvent("Escape"),
            description = "exit",
            condition = { filter.isEmpty() },
            action = { exit() }
        )

        val autocompleteFilter = KeyAction(
            key = config.keyAutocompleteFilter,
            description = "autocomplete",
            condition = { filter.isNotEmpty() && items.isNotEmpty() },
            action = {
                val commonPrefix = items
                    .map { it.path.name.lowercase() }
                    .filter { it.startsWith(filter.lowercase()) }
                    .commonPrefix()
                filtered(commonPrefix)
            }
        )
        val clearFilter = KeyAction(
            key = config.keyClearFilter,
            description = "clear filter",
            condition = { filter.isNotEmpty() },
            action = { filtered("") }
        )

        fun tryHandle(event: KeyboardEvent, state: State): State? {
            val actions = listOf(
                cursorUp, cursorDown, cursorHome, cursorEnd,
                navigateUp, navigateInto, navigateOpen,
                exitCD, exit,
                autocompleteFilter, clearFilter
            )
            for (action in actions) {
                if (action.matches(event, state)) return action.action(state, event)
            }
            if (!event.alt && !event.ctrl) when {
                event == KeyboardEvent("Backspace") -> return state.filtered(state.filter.dropLast(1))
                event.key.length == 1 -> return state.filtered(state.filter + event.key)
            }
            return null
        }
    }

    private data class KeyAction(
        val key: KeyboardEvent,
        val description: String? = null,
        val style: TextStyle? = null,
        private val condition: State.() -> Boolean,
        val action: State.(KeyboardEvent) -> State
    ) {
        fun matches(event: KeyboardEvent, state: State) = key == event && available(state)
        fun available(state: State) = state.condition()
    }


    private val animation = terminal.animation<State> { s ->
        val entries = s.filteredItems
        val selectedIndex = s.cursor

        val filterPrompt = terminal.theme.style("select.cursor")("$RealSystemPathSeparator")
        val titleStyle = TextColors.rgb("1dff7b")
        val filterStyle = TextStyle(TextColors.brightWhite)
        val title = Text(when {
            s.filter.isNotEmpty() -> "${titleStyle("${s.directory}")} $filterPrompt ${filterStyle(s.filter)}"
            else -> titleStyle("${s.directory}")
        })

        val bottom = if (config.showInstructions) Text(buildInstructions(s)) else null

        val dirStyle = TextColors.rgb("2fa2ff")
        val fileStyle = TextStyle(TextColors.brightWhite)
        val linkStyle = TextStyle(TextColors.brightCyan)

        val highlighted = TextStyle(inverse = true)

        table {
            captionTop(title)
            if (bottom != null) captionBottom(bottom)

            cellBorders = Borders.LEFT_RIGHT
            tableBorders = Borders.NONE
            borderType = BorderType.BLANK
            padding = Padding(0)
            whitespace = Whitespace.PRE_WRAP

            header {
                style = dim.style
                row {
                    cell("Permissions")
                    cell("Size") {
                        align = TextAlign.RIGHT
                    }
                    cell("Last Modified")
                    cell("Name")
                }
            }
            body {
                val padding = config.limit / 2 - 1
                val firstVisible = (s.cursor - padding).coerceAtMost(entries.size - config.limit).coerceAtLeast(0)

                for ((i, entry) in entries.withIndex()) {
                    if (i < firstVisible) continue

                    fun SectionBuilder.more(n: Int) {
                        row {
                            cell("")
                            cell("")
                            cell("")
                            cell("… $n more") {
                                style = dim.style
                            }
                        }
                    }

                    if (i == firstVisible && firstVisible > 0) {
                        more(firstVisible + 1)
                        continue
                    }
                    if (i == firstVisible + config.limit - 1 && firstVisible + config.limit < entries.size) {
                        more(entries.size - (firstVisible + config.limit) + 1)
                        break
                    }

                    row {

                        cell(renderPermissions(entry.stat))

                        cell(entry.size?.let { renderFileSize(it) } ?: "") {
                            align = TextAlign.RIGHT
                        }

                        cell(renderTime(entry.stat.lastModificationTime))

                        val name = entry.path.name
                            .let {
                                if (s.filter.isNotEmpty()) {
                                    // highlight all filter occurrences
                                    var index = 0
                                    var result = ""
                                    while (index < it.length) {
                                        val found = it.indexOf(s.filter, index, ignoreCase = true)
                                        if (found < 0) {
                                            result += it.substring(index, it.length)
                                            break
                                        }
                                        result += it.substring(index, found)
                                        index = found

                                        result += TextColors.brightRed(it.substring(index, index + s.filter.length))
                                        index += s.filter.length
                                    }
                                    result
                                } else it
                            }
                            .let { if (i == selectedIndex) highlighted(it) else it }
                            .let { "\u0006$it" } // prevent filter highlighting from getting removed
                            .let {
                                when {
                                    entry.isDirectory -> "${dirStyle(it)}$RealSystemPathSeparator"
                                    entry.isRegularFile -> fileStyle(it)
                                    entry.isSymbolicLink -> "${linkStyle(it)} ${dim("->")} "
                                    else -> TextColors.magenta(it)
                                }
                            }
                        cell(name)
                    }
                }
            }
        }
    }

    init {
        animation.update(state)
    }

    override fun stop() = animation.stop()
    override fun clear() = animation.clear()

    override fun receiveEvent(event: InputEvent): Status<Path?> {
        if (event !is KeyboardEvent) return Status.Continue
        state = when {
            event.isCtrlC -> state.exit()
            else -> actions.tryHandle(event, state) ?: state
        }
        animation.update(state)
        return when {
            state.exit != null -> {
                if (config.clearOnExit) animation.clear()
                else animation.stop()
                Status.Finished(state.exit.takeUnless { it == workingDirectory })
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
        state: State
    ): String {
        val styleKey = TextStyle(TextColors.brightWhite, bold = true)

        fun MutableList<String>.render(action: KeyAction) {
            if (!action.available(state)) return
            add(when (action.description) {
                null -> styleKey(keyName(action.key))
                else -> "${styleKey(keyName(action.key))} ${dim(action.description)}"
            }.let { action.style?.let { style -> style(it) } ?: it })
        }

        fun MutableList<String>.group(block: MutableList<String>.() -> Unit) {
            add(mutableListOf<String>().apply(block).joinToString(" "))
        }

        return buildList {

            render(actions.navigateUp)

            group {
                render(actions.cursorUp)
                render(actions.cursorDown)
            }

            render(actions.navigateInto)
            render(actions.navigateOpen)

            render(actions.autocompleteFilter)
            render(actions.clearFilter)

            render(actions.exitCD)
            render(actions.exit)

        }.joinToString(dim(" • "))
    }

    private fun renderPermissions(stat: Stat): String {
        val mode = stat.mode
        val user = mode.user
        val group = mode.group
        val others = mode.others

        fun render(perm: Stat.Mode.Permissions): String {
            val r = if (perm.canRead) TextColors.red("r") else dim("-")
            val w = if (perm.canWrite) TextColors.green("w") else dim("-")
            val x = if (perm.canExecute) TextColors.brightBlue("x") else dim("-")
            return "$r$w$x"
        }

        return "${render(user)}${render(group)}${render(others)}"
    }

    private fun renderFileSize(bytes: Long): String {
        val numStyle = TextStyle(TextColors.brightYellow)
        val unitStyle = TextStyle(TextColors.brightYellow, dim = true)

        val units = listOf("k", "M", "G", "T", "P")

        if (bytes < 1000) {
            return numStyle("$bytes")
        }

        var value = bytes / 1000.0
        var i = 0
        while (value >= 1000 && i + 1 < units.size) {
            value /= 1000.0
            i++
        }

        fun Double.format(): String {
            toString().take(3).let {
                if (it.endsWith('.')) return it.dropLast(1)
                return it
            }
        }

        return "${numStyle(value.format())}${unitStyle(units[i])}"
    }

    private fun renderTime(instant: Instant): String {
        val now = Clock.System.now()
        val duration = now - instant
        val format = if (duration > 365.days) DateTimeComponents.Format {
            dayOfMonth()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars("  ")
            year()
        } else DateTimeComponents.Format {
            dayOfMonth()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            hour()
            chars(":")
            minute()
        }

        val halfBrightnessAtHours = 6L
        val brightness = 1L / ((duration.inWholeMinutes / (halfBrightnessAtHours * 60L)) + 1)
        val color = TextColors.hsv(120, 1, brightness * 0.6 + 0.4)
        return color(instant.format(format))
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


operator fun Path.div(child: String) = Path(this, child).cleaned()

fun Path.cleaned() = Path("$this".replace(SystemPathSeparator, RealSystemPathSeparator))

expect val RealSystemPathSeparator: Char





expect val platformName: String
