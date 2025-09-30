package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.Entry
import de.jonasbroeckmann.nav.Entry.Type.*
import de.jonasbroeckmann.nav.printlnOnDebug
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime
import de.jonasbroeckmann.nav.app.State as UIState

@OptIn(ExperimentalTime::class)
class UI(
    context: FullContext,
    private val actions: Actions
) : FullContext by context {
    private val animation = terminal.animation<UIState> { render(it) }

    fun update(state: UIState) = animation.update(state)

    fun clear() = animation.clear()

    fun stop() = animation.stop()

    private fun render(data: UIState): Widget = context(data) {
        verticalLayout {
            printlnOnDebug { "Updating UI ..." }

            align = TextAlign.LEFT

            var additionalRows = 0

            val top = renderTitle(
                directory = data.directory,
                filter = data.filter,
                showCursor = !data.isTypingCommand && !data.inQuickMacroMode
            )
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

    private val selectedNamePrefix get() = when {
        accessibilityDecorations -> "▊"
        else -> ""
    }

    private val unselectedNamePrefix get() = when {
        accessibilityDecorations -> " "
        else -> ""
    }

    private fun renderTable(
        entries: List<Entry>,
        cursor: Int,
        filter: String,
        additionalRows: Int
    ) = table {
        overflowWrap = OverflowWrap.ELLIPSES
        cellBorders = Borders.LEFT_RIGHT
        tableBorders = Borders.NONE
        borderType = BorderType.BLANK
        padding = Padding(0)

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
                config.shownColumns.forEach { column ->
                    cell(column.title)
                }
                cell("${unselectedNamePrefix}Name")
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
                        config.shownColumns.forEach { _ ->
                            cell("")
                        }
                        cell("$unselectedNamePrefix… $n more") {
                            style = TextStyles.dim.style
                        }
                    }
                }
            ) { entry, isSelected ->
                row {
                    val error = entry.error
                        ?.lineSequence()
                        ?.joinToString(" ")
                        ?.trim { it.isWhitespace() }
                    if (error != null) {
                        cell(TextColors.red(error)) {
                            columnSpan = config.shownColumns.size
                            align = TextAlign.CENTER
                        }
                    } else {
                        config.shownColumns.forEach { column ->
                            cell(column.render(entry))
                        }
                    }
                    cell(
                        Text(
                            text = renderName(
                                entry = entry,
                                isSelected = isSelected,
                                filter = filter
                            )
                        )
                    )
                }
            }
        }
    }

    private fun SectionBuilder.renderEntries(
        entries: List<Entry>,
        cursor: Int,
        otherRows: Int,
        renderMore: SectionBuilder.(Int) -> Unit,
        renderEntry: SectionBuilder.(Entry, Boolean) -> Unit
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

    private fun renderTitle(directory: Path, filter: String, showCursor: Boolean): String {
        return "${renderPath(directory)}${renderFilter(filter, showCursor)}"
    }

    private fun renderFilter(filter: String, showCursor: Boolean): String {
        if (filter.isEmpty()) return ""
        val style = TextColors.rgb(colors.filter) + TextStyles.bold
        return buildString {
            append(" $RealSystemPathSeparator ")
            append(style(filter))
            if (showCursor) append(style("_"))
        }
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun renderName(entry: Entry, isSelected: Boolean, filter: String): String {
        val filterMarkerStyle = TextColors.rgb(colors.filterMarker) + TextStyles.bold
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
                } else {
                    it
                }
            }
            .let { if (isSelected) selectedStyle(it) else it }
            .let { "\u0006$it" } // prevent filter highlighting from getting removed
            .dressUpEntryName(entry, showLinkTarget = true)
            .let { if (isSelected) "$selectedNamePrefix$it" else "$unselectedNamePrefix$it" }
    }

    private fun renderPath(path: Path): String {
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

        val style = TextColors.rgb(colors.path)
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
                    cell(
                        renderAction(item).let { rendered ->
                            item.selectedStyle?.let { it(rendered) + " " } ?: rendered
                        }
                    )
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
        val styleKey = TextColors.rgb(colors.keyHints) + TextStyles.bold
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
            render(actions.exitMenu)

            render(actions.discardCommand)

            render(actions.exitCD)
            render(actions.exit)

            if (!state.isMenuOpen) {
                render(actions.openMenu)
            }
        }

        if (debugMode) {
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

    companion object {
        @Suppress("detekt:CyclomaticComplexMethod")
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

        context(context: FullContext)
        val Entry?.style get() = when (this?.type) {
            null -> TextColors.magenta
            SymbolicLink -> TextColors.rgb(context.colors.link)
            Directory -> TextColors.rgb(context.colors.directory)
            RegularFile -> TextColors.rgb(context.colors.file)
            Unknown -> TextColors.magenta
        }

        context(context: FullContext)
        fun String.dressUpEntryName(entry: Entry, showLinkTarget: Boolean = false): String {
            val dirStyle = TextColors.rgb(context.colors.directory)
            val fileStyle = TextColors.rgb(context.colors.file)
            val linkStyle = TextColors.rgb(context.colors.link)
            return when (entry.type) {
                else if entry.error != null -> "${TextStyles.dim(this)} "
                SymbolicLink -> when (showLinkTarget) {
                    true -> {
                        val linkTarget = entry.linkTarget
                        val renderedLinkTarget = linkTarget?.path?.toString()?.dressUpEntryName(
                            linkTarget.targetEntry,
                            showLinkTarget = false
                        ) ?: "${TextStyles.dim("?")} "
                        "${linkStyle(this)} ${TextStyles.dim("->")} $renderedLinkTarget"
                    }
                    false -> "${linkStyle(this)} "
                }
                Directory -> "${dirStyle(this)}$RealSystemPathSeparator"
                RegularFile -> "${fileStyle(this)} "
                Unknown -> "${TextColors.magenta(this)} "
            }
        }
    }
}
