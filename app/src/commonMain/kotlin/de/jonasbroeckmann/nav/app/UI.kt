package de.jonasbroeckmann.nav.app

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.animation.Animation
import com.github.ajalt.mordant.input.*
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.Config
import de.jonasbroeckmann.nav.ConfigProvider
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.Stat
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.io.files.Path
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import de.jonasbroeckmann.nav.app.State as UIState


@OptIn(ExperimentalTime::class)
class UI(
    terminal: Terminal,
    override val config: Config,
    private val actions: Actions
) : Animation<UIState>(
    terminal = terminal
), ConfigProvider {

    override fun renderData(data: UIState): Widget = context(data) {
        verticalLayout {
            if (data.debugMode) terminal.println("Updating UI ...")

            align = TextAlign.LEFT

            var additionalRows = 0

            val top = renderTitle(data.directory, data.filter, data.debugMode)
            additionalRows += 1

            val bottom = renderBottom { additionalRows += it }

            val table = renderTable(
                entries = data.filteredItems,
                cursor = data.cursor,
                filter = data.filter,
                additionalRows = additionalRows
            )

            cell(top)
            cell(table)
            cell(bottom)
        }
    }

    private fun renderTable(
        entries: List<UIState.Entry>,
        cursor: Int,
        filter: String,
        additionalRows: Int
    ) = table {
        overflowWrap = OverflowWrap.ELLIPSES
        cellBorders = Borders.LEFT_RIGHT
        tableBorders = Borders.NONE
        borderType = BorderType.BLANK
        padding = Padding(0)
        whitespace = Whitespace.PRE_WRAP

        var additionalRows = additionalRows

        if (entries.isEmpty()) {
            body {
                if (filter.isNotEmpty()) {
                    row { cell(Text(TextStyles.dim("No results …"))) }
                    additionalRows += 1
                } else {
                    row { cell(Text(TextStyles.dim("There is nothing here"))) }
                    additionalRows += 1
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
            additionalRows += 1
        }
        body {
            renderEntries(
                entries = entries,
                cursor = cursor,
                otherRows = additionalRows,
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
                    if (entry.statError != null) {
                        cell(TextColors.red(entry.statError.message)) {
                            columnSpan = 3
                            align = TextAlign.CENTER
                        }
                    } else {
                        cell(
                            Text(
                                text = renderPermissions(entry.stat),
                                width = 9
                            )
                        )
                        cell(
                            Text(
                                text = renderFileSize(entry.size),
                                align = TextAlign.RIGHT,
                                width = 4
                            )
                        )
                        cell(
                            Text(
                                text = renderModificationTime(entry.stat.lastModificationTime),
                                width = 12
                            )
                        )
                    }
                    cell(Text(
                        text = renderName(
                            entry = entry,
                            isSelected = isSelected,
                            filter = filter
                        )
                    ))
                }
            }
        }
    }

    private fun SectionBuilder.renderEntries(
        entries: List<UIState.Entry>,
        cursor: Int,
        otherRows: Int,
        renderMore: SectionBuilder.(Int) -> Unit,
        renderEntry: SectionBuilder.(UIState.Entry, Boolean) -> Unit
    ) {
        var maxVisible = if (config.maxVisibleEntries == 0) entries.size else config.maxVisibleEntries
        if (config.limitToTerminalHeight) {
            terminal.updateSize()
            maxVisible = maxVisible.coerceAtMost(terminal.size.height - otherRows)
        }
        maxVisible = maxVisible.coerceAtLeast(1)

        val padding = maxVisible / 2 - 1
        val firstVisible = (cursor - padding)
            .coerceAtMost(entries.size - maxVisible)
            .coerceAtLeast(0)
        for ((i, entry) in entries.withIndex()) {
            if (i < firstVisible) continue
            if (i == firstVisible && firstVisible > 0) {
                renderMore(firstVisible + 1)
                continue
            }
            if (i == firstVisible + maxVisible - 1 && firstVisible + maxVisible < entries.size) {
                renderMore(entries.size - (firstVisible + maxVisible) + 1)
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
                    entry.statError != null -> "${TextStyles.dim(it)} "
                    entry.isDirectory -> "${dirStyle(it)}$RealSystemPathSeparator"
                    entry.isRegularFile -> "${fileStyle(it)} "
                    entry.isSymbolicLink -> "${linkStyle(it)} ${TextStyles.dim("->")} "
                    else -> "${TextColors.magenta(it)} "
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

    context(state: UIState)
    private fun renderBottom(
        collectAdditionalRows: (Int) -> Unit
    ): Widget = verticalLayout {
        align = TextAlign.LEFT
        this.width = ColumnWidth.Expand()

        if (!config.hideHints) {
            cell(renderNavHints())
            collectAdditionalRows(1)
        }

        if (state.isMenuOpen) {
            cell(renderMenu(collectAdditionalRows))
            if (!config.hideHints) {
                cell("${TextStyles.dim("•")} ${renderMenuHints()}")
                collectAdditionalRows(1)
            }
        }
    }

    context(state: UIState)
    private fun renderMenu(
        collectAdditionalRows: (Int) -> Unit
    ) = grid {
        state.availableMenuActions.forEachIndexed { i, item ->
            row {
                cell(TextStyles.dim("│"))
                val isSelected = i == state.coercedMenuCursor
                if (isSelected) {
                    cell(renderAction(actions.menuSubmit))
                    cell(renderAction(item).let { rendered ->
                        item.selectedStyle?.let { it(rendered) + " " } ?: rendered
                    })
                } else {
                    cell("")
                    cell(renderAction(item))
                }
            }
        }
        collectAdditionalRows(state.availableMenuActions.size)
    }

    context(state: UIState)
    private fun renderAction(action: Action<*>): String {
        val styleKey = TextColors.rgb(config.colors.keyHints) + TextStyles.bold
        val keyStr = when (action) {
            is KeyAction -> action.displayKey(state)?.let { styleKey(keyName(it)) }
            is MenuAction -> null
        }
        val desc = action.description(state)
        val descStr = when (action) {
            is KeyAction -> desc?.let { TextStyles.dim(it) }
            is MenuAction -> desc
        }
        val str = listOfNotNull(
            keyStr,
            descStr
        ).joinToString(" ")
        action.style()?.let { return it(str) }
        return str
    }

    private fun buildHints(
        block: RenderHintsScope.() -> Unit
    ): String {
        val scope = RenderHintsScope().apply(block)
        return scope.joinToString(
            separator = TextStyles.dim(" • "),
            prefix = scope.prefix
        )
    }

    private inner class RenderHintsScope : MutableList<String> by mutableListOf() {
        var prefix: String = ""

        context(state: UIState)
        fun render(action: KeyAction) {
            if (!action.isAvailable(state)) return
            add(renderAction(action))
        }

        fun group(block: RenderHintsScope.() -> Unit) {
            RenderHintsScope().apply(block).let {
                if (it.isNotEmpty()) add(it.joinToString(" "))
            }
        }
    }

    context(state: UIState)
    private fun renderNavHints() = buildHints {
        if (state.inQuickMacroMode) {
            prefix = state.currentEntry.style("Entry") + TextStyles.dim(" │ ")
            val availableMacros = actions.quickMacroActions.filter { it.isAvailable(state) }
            availableMacros.forEach {
                render(it)
            }
            if (availableMacros.size <= 1) { // cancel is always available
                add(TextStyles.dim("No entry macros available"))
            }
        } else {
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

            if (!state.isMenuOpen) {
                render(actions.openMenu)
            }
        }

        if (state.debugMode) {
            if (state.inQuickMacroMode) {
                add(TextStyles.dim("M"))
            }
            if (state.lastReceivedEvent != null) {
                add(TextStyles.dim("Key: ${keyName(state.lastReceivedEvent)}"))
            }
        }
    }

    context(state: UIState)
    private fun renderMenuHints() = buildHints {
        if (state.coercedMenuCursor == 0) {
            render(actions.closeMenu)
        }
        group {
            if (state.coercedMenuCursor > 0) render(actions.menuUp)
            render(actions.menuDown)
            if (this.isNotEmpty()) add(TextStyles.dim("navigate"))
        }
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
        val format = if (duration.absoluteValue > 365.days) DateTimeComponents.Format {
            day()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars("  ")
            year()
        } else DateTimeComponents.Format {
            day()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            hour()
            chars(":")
            minute()
        }

        val hoursSinceInstant = (duration.inWholeMinutes / 60.0).coerceAtLeast(0.0)
        val factor = 2.0.pow(-hoursSinceInstant / config.modificationTime.halfBrightnessAtHours)

        val brightnessRange = config.modificationTime.minimumBrightness..1.0
        val brightness = factor * (brightnessRange.endInclusive - brightnessRange.start) + brightnessRange.start

        val rgb = RGB(config.colors.modificationTime)
        val style = TextColors.color(rgb.toHSV().copy(v = brightness.toFloat()))
        return style(instant.format(format))
    }

    companion object {
        fun keyName(key: KeyboardEvent): String {
            var k = when (key.key) {
                "Enter" -> "enter"
                "Escape" -> "esc"
                "Tab" -> "tab"
                "ArrowUp" -> "↑"
                "ArrowDown" -> "↓"
                "ArrowLeft" -> "←"
                "ArrowRight" -> "→"
                "PageUp" -> "page↑"
                "PageDown" -> "page↓"
                else -> key.key
            }
            if (key.alt) k = "alt+$k"
            if (key.shift && key.key.length > 1) k = "shift+$k"
            if (key.ctrl) k = "ctrl+$k"
            return k
        }

        context(configProvider: ConfigProvider)
        val UIState.Entry?.style get() = when {
            this == null -> TextColors.magenta
            isDirectory -> TextColors.rgb(configProvider.config.colors.directory)
            isRegularFile -> TextColors.rgb(configProvider.config.colors.file)
            isSymbolicLink -> TextColors.rgb(configProvider.config.colors.link)
            else -> TextColors.magenta
        }
    }
}
