package de.jonasbroeckmann.nav

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.animation.StoppableAnimation
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
import kotlin.math.pow
import kotlin.time.Duration.Companion.days

class MainAnimation(
    terminal: Terminal,
    private val config: Config,
    private val actions: Actions
) : StoppableAnimation {



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
                s.filter.isNotEmpty() -> "${renderPath(s.directory, s.debugMode)} $RealSystemPathSeparator ${filterStyle(s.filter)}"
                else -> renderPath(s.directory, s.debugMode)
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
                                renderModificationTime(entry.stat.lastModificationTime),
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

    override fun stop() = animation.stop()
    override fun clear() = animation.clear()

    fun update(state: State) = animation.update(state)


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