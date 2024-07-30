package de.jonasbroeckmann.nav.app

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.animation.Animation
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.SectionBuilder
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.io.files.Path
import kotlin.math.pow
import kotlin.time.Duration.Companion.days
import de.jonasbroeckmann.nav.app.State as UIState


class UI(
    terminal: Terminal,
    private val config: Config,
    private val actions: Actions
) : Animation<UIState>(
    trailingLinebreak = true,
    terminal = terminal
) {
    override fun renderData(data: UIState): Widget = table {
        overflowWrap = OverflowWrap.ELLIPSES
        cellBorders = Borders.LEFT_RIGHT
        tableBorders = Borders.NONE
        borderType = BorderType.BLANK
        padding = Padding(0)
        whitespace = Whitespace.PRE_WRAP

        captionTop(renderTitle(data.directory, data.filter, data.debugMode), align = TextAlign.LEFT)
        if (!config.hideHints) captionBottom(renderHints(data), align = TextAlign.LEFT)

        if (data.filteredItems.isEmpty()) {
            body {
                row {
                    if (data.filter.isNotEmpty()) {
                        cell(Text(TextStyles.dim("No results …")))
                    } else {
                        cell(Text(TextStyles.dim("There is nothing here")))
                    }
                }
            }
            return@table
        }

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
            renderEntries(
                entries = data.filteredItems,
                cursor = data.cursor,
                renderMore = { n ->
                    row {
                        cell("")
                        cell("")
                        cell("")
                        cell("… $n more") {
                            style = TextStyles.dim.style
                        }
                    }
                }
            ) { entry, isSelected ->
                row {
                    cell(Text(
                        text = renderPermissions(entry.stat),
                        width = 9
                    ))
                    cell(Text(
                        text = renderFileSize(entry.size),
                        align = TextAlign.RIGHT,
                        width = 4
                    ))
                    cell(Text(
                        text = renderModificationTime(entry.stat.lastModificationTime),
                        width = 12
                    ))
                    cell(Text(
                        text = renderName(
                            entry = entry,
                            isSelected = isSelected,
                            filter = data.filter
                        )
                    ))
                }
            }
        }
    }

    private fun SectionBuilder.renderEntries(
        entries: List<UIState.Entry>,
        cursor: Int,
        renderMore: SectionBuilder.(Int) -> Unit,
        renderEntry: SectionBuilder.(UIState.Entry, Boolean) -> Unit
    ) {
        val padding = config.maxVisibleEntries / 2 - 1
        val firstVisible = (cursor - padding)
            .coerceAtMost(entries.size - config.maxVisibleEntries)
            .coerceAtLeast(0)
        for ((i, entry) in entries.withIndex()) {
            if (i < firstVisible) continue
            if (i == firstVisible && firstVisible > 0) {
                renderMore(firstVisible + 1)
                continue
            }
            if (i == firstVisible + config.maxVisibleEntries - 1 && firstVisible + config.maxVisibleEntries < entries.size) {
                renderMore(entries.size - (firstVisible + config.maxVisibleEntries) + 1)
                break
            }
            renderEntry(entry, i == cursor)
        }
    }

    private fun renderTitle(directory: Path, filter: String, debugMode: Boolean): String {
        return "${renderPath(directory, debugMode)}${renderFilter(filter)}"
    }

    private fun renderFilter(filter: String): String {
        if (filter.isEmpty()) return ""
        val style = TextColors.rgb(config.colors.filter) + TextStyles.bold
        return " $RealSystemPathSeparator ${style(filter)}"
    }

    private fun renderName(entry: UIState.Entry, isSelected: Boolean, filter: String): String {
        val filterMarkerStyle = TextColors.rgb(config.colors.filterMarker) + TextStyles.bold
        val dirStyle = TextColors.rgb(config.colors.directory)
        val fileStyle = TextColors.rgb(config.colors.file)
        val linkStyle = TextColors.rgb(config.colors.link)
        val selectedStyle = TextStyles.inverse
        return entry.path.name
            .let {
                if (filter.isNotEmpty()) {
                    // highlight all filter occurrences
                    var index = 0
                    var result = ""
                    while (index < it.length) {
                        val found = it.indexOf(filter, index, ignoreCase = true)
                        if (found < 0) {
                            result += it.substring(index, it.length)
                            break
                        }
                        result += it.substring(index, found)
                        index = found

                        result += filterMarkerStyle(it.substring(index, index + filter.length))
                        index += filter.length
                    }
                    result
                } else it
            }
            .let { if (isSelected) selectedStyle(it) else it }
            .let { "\u0006$it" } // prevent filter highlighting from getting removed
            .let {
                when {
                    entry.isDirectory -> "${dirStyle(it)}$RealSystemPathSeparator"
                    entry.isRegularFile -> fileStyle(it)
                    entry.isSymbolicLink -> "${linkStyle(it)} ${TextStyles.dim("->")} "
                    else -> TextColors.magenta(it)
                }
            }
    }

    private fun renderPath(path: Path, debugMode: Boolean): String {
        val pathString = path.toString().let {
            val home = UserHome.toString().removeSuffix("$RealSystemPathSeparator")
            if (it.startsWith(home)) " ~${it.removePrefix(home)}" else it
        }
        val elements = pathString.split(RealSystemPathSeparator)

        val max = config.maxVisiblePathElements
        val shortened = when {
            elements.size > max -> {
                elements.subList(0, 1) + listOf("…") + elements.subList(elements.size - (max - 2), elements.size)
            }
            else -> elements
        }

        val style = TextColors.rgb(config.colors.path)
        return style(shortened.joinToString(" $RealSystemPathSeparator ")).let {
            if (debugMode) "$path\n$it" else it
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
        if (key.shift && key.key.length > 1) k = "shift+$k"
        if (key.ctrl) k = "ctrl+$k"
        return k
    }

    private fun renderHints(
        state: UIState
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
            mutableListOf<String>().apply(block).let {
                if (it.isNotEmpty()) add(it.joinToString(" "))
            }
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

            if (state.debugMode && state.lastReceivedEvent != null) {
                add(TextStyles.dim("Key: ${keyName(state.lastReceivedEvent)}"))
            }

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

    private fun renderFileSize(bytes: Long?): String {
        if (bytes == null) return ""

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

    private fun renderModificationTime(instant: Instant): String {
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

        val hours = duration.inWholeMinutes / 60.0
//        val factor = 1.0 / ((hours / config.modificationTime.halfBrightnessAtHours) + 1)
        val factor = 2.0.pow(-hours / config.modificationTime.halfBrightnessAtHours)

        val brightnessRange = config.modificationTime.minimumBrightness..1.0
        val brightness = factor * (brightnessRange.endInclusive - brightnessRange.start) + brightnessRange.start

        val rgb = RGB(config.colors.modificationTime)
        val style = TextColors.color(rgb.toHSV().copy(v = brightness.toFloat()))
        return style(instant.format(format))
    }
}