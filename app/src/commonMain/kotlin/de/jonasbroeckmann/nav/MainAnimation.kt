package de.jonasbroeckmann.nav

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.rendering.*
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
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Duration.Companion.days

class MainAnimation(
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
            if (filter.isEmpty()) return@lazy items
            items.filter { filter.lowercase() in it.path.name.lowercase() }
                .sortedByDescending { it.path.name.startsWith(filter) }
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
            val newCursor = if (tmp.filteredItems.size < filteredItems.size) 0 else tmp.cursor
            return tmp.copy(items = tmp.items, cursor = newCursor)
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
        fun resetExit() = if (exit != null) copy(exit = null) else this

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
    private class Actions(config: Config) {
        val cursorUp = KeyAction(
            key = config.keys.cursor.up,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(cursor - 1) }
        )
        val cursorDown = KeyAction(
            key = config.keys.cursor.down,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(cursor + 1) }
        )
        val cursorHome = KeyAction(
            key = config.keys.cursor.home,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(0) }
        )
        val cursorEnd = KeyAction(
            key = config.keys.cursor.end,
            condition = { filteredItems.isNotEmpty() },
            action = { withCursor(filteredItems.lastIndex) }
        )

        val navigateUp = KeyAction(
            key = config.keys.nav.up,
            condition = { directory.parent != null },
            action = { navigatedUp() }
        )
        val navigateInto = KeyAction(
            key = config.keys.nav.into,
            condition = { currentEntry?.isDirectory == true },
            action = { navigatedInto(currentEntry) }
        )
        val navigateOpen = KeyAction(
            key = config.keys.nav.open,
            description = "open in ${config.editor}",
            style = TextColors.rgb(config.colors.file),
            condition = { currentEntry?.isRegularFile == true },
            action = { exit(currentEntry?.path ?: throw IllegalStateException("Cannot open file")) }
        )

        val exitCD = KeyAction(
            key = config.keys.submit,
            description = "exit here",
            style = TextColors.rgb(config.colors.path),
            condition = { directory != workingDirectory && filter.isEmpty() },
            action = { exit(directory) }
        )
        val exit = KeyAction(
            key = config.keys.cancel,
            description = "cancel",
            condition = { filter.isEmpty() },
            action = { exit() }
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
                filtered(commonPrefix)
            }
        )
        val clearFilter = KeyAction(
            key = config.keys.filter.clear,
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

        val filterStyle = TextColors.rgb(config.colors.filter) + TextStyles.bold
        val filterMarkerStyle = TextColors.rgb(config.colors.filterMarker) + TextStyles.bold
        val dirStyle = TextColors.rgb(config.colors.directory)
        val fileStyle = TextColors.rgb(config.colors.file)
        val linkStyle = TextColors.rgb(config.colors.link)


        val entries = s.filteredItems
        val selectedIndex = s.cursor

        val title = Text(
            when {
                s.filter.isNotEmpty() -> "${renderPath(s.directory)} $RealSystemPathSeparator ${filterStyle(s.filter)}"
                else -> renderPath(s.directory)
            }
        )

        val bottom = if (config.hideHints) null else Text(renderHints(s))


        val highlighted = TextStyle(inverse = true)

        table {
            captionTop(title)
            if (bottom != null) captionBottom(bottom)

            overflowWrap = OverflowWrap.ELLIPSES
            cellBorders = Borders.LEFT_RIGHT
            tableBorders = Borders.NONE
            borderType = BorderType.BLANK
            padding = Padding(0)
            whitespace = Whitespace.PRE_WRAP

            header {
                style = TextStyles.dim.style
                row {
                    cell("Permissions")
                    cell("Size") {
                        align = TextAlign.RIGHT
                    }
                    cell(Text("Last Modified", overflowWrap = OverflowWrap.ELLIPSES))
                    cell("Name")
                }
            }
            body {
                val padding = config.maxVisibleEntries / 2 - 1
                val firstVisible =
                    (s.cursor - padding).coerceAtMost(entries.size - config.maxVisibleEntries).coerceAtLeast(0)

                for ((i, entry) in entries.withIndex()) {
                    if (i < firstVisible) continue

                    fun SectionBuilder.more(n: Int) {
                        row {
                            cell("")
                            cell("")
                            cell("")
                            cell("… $n more") {
                                style = TextStyles.dim.style
                            }
                        }
                    }

                    if (i == firstVisible && firstVisible > 0) {
                        more(firstVisible + 1)
                        continue
                    }
                    if (i == firstVisible + config.maxVisibleEntries - 1 && firstVisible + config.maxVisibleEntries < entries.size) {
                        more(entries.size - (firstVisible + config.maxVisibleEntries) + 1)
                        break
                    }

                    row {

                        cell(Text(renderPermissions(entry.stat), width = 9))

                        cell(Text(
                            text = entry.size?.let { renderFileSize(it) } ?: "",
                            align = TextAlign.RIGHT,
                            width = 4
                        ))

                        cell(
                            Text(
                                renderTime(entry.stat.lastModificationTime),
                                width = 12
                            )
                        )

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

                                        result += filterMarkerStyle(it.substring(index, index + s.filter.length))
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
                                    entry.isSymbolicLink -> "${linkStyle(it)} ${TextStyles.dim("->")} "
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

    fun terminate() {
        if (config.clearOnExit) animation.clear()
        else animation.stop()
    }

    override fun receiveEvent(event: InputEvent): InputReceiver.Status<Path?> {
        if (event !is KeyboardEvent) return InputReceiver.Status.Continue
        state = when {
            event.isCtrlC -> state.exit()
            else -> actions.tryHandle(event, state) ?: state
        }
        animation.update(state)

        val exit = state.exit ?: return InputReceiver.Status.Continue
        state = state.resetExit()
        return InputReceiver.Status.Finished(exit.takeUnless { it == workingDirectory })
    }

    private fun renderPath(path: Path): String {
        val style = TextColors.rgb(config.colors.path)
        val elements = buildList {
            var p = path
            do {
                if (p == userHome) {
                    add(" ~")
                    break
                }
                add(p.name)
                p = p.parent ?: break
            } while (true)
        }.asReversed().let {
            if (it.size > config.maxVisiblePathElements) {
                it.subList(0, 1) + listOf("…") + it.subList(it.size - (config.maxVisiblePathElements - 2), it.size)
            } else it
        }

        return style(elements.joinToString(" $RealSystemPathSeparator "))
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
        if (key.shift && key.key.length > 1) k = "shift+$k"
        if (key.ctrl) k = "ctrl+$k"
        return k
    }

    private fun renderHints(
        state: State
    ): String {
        val styleKey = TextColors.rgb(config.colors.keyHints) + TextStyles.bold

        fun MutableList<String>.render(action: KeyAction) {
            if (!action.available(state)) return
            add(when (action.description) {
                null -> styleKey(keyName(action.key))
                else -> "${styleKey(keyName(action.key))} ${TextStyles.dim(action.description)}"
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

        }.joinToString(TextStyles.dim(" • "))
    }

    private fun renderPermissions(stat: Stat): String {
        val styleRead = TextColors.rgb(config.colors.permissionRead)
        val styleWrite = TextColors.rgb(config.colors.permissionWrite)
        val styleExecute = TextColors.rgb(config.colors.permissionExecute)

        fun render(perm: Stat.Mode.Permissions): String {
            val r = if (perm.canRead) styleRead("r") else TextStyles.dim("-")
            val w = if (perm.canWrite) styleWrite("w") else TextStyles.dim("-")
            val x = if (perm.canExecute) styleExecute("x") else TextStyles.dim("-")
            return "$r$w$x"
        }

        return "${render(stat.mode.user)}${render(stat.mode.group)}${render(stat.mode.others)}"
    }

    private fun renderFileSize(bytes: Long): String {
        val numStyle = TextColors.rgb(config.colors.entrySize)
        val unitStyle = numStyle + TextStyles.dim

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

        val rgb = RGB(config.colors.modificationTime)
        val style = TextColors.color(rgb.toHSV().copy(v = (brightness * 0.6 + 0.4).toFloat()))
        return style(instant.format(format))
    }
}