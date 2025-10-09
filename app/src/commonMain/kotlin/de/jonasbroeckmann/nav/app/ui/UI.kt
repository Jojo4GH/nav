package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.animation.StoppableAnimation
import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.MainActions
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.io.files.Path
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UI(
    context: FullContext,
    private val actions: MainActions
) : FullContext by context, UpdatableAnimation<State>, StoppableAnimation {
    private val animation = terminal.animation<State> { state ->
        context(state) { render() }
    }

    private var shownState: State? = null

    override fun update(state: State) {
        if (state == shownState) {
            printlnOnDebug { "Skipping UI update with unchanged state ..." }
            return
        }
        shownState = state
        animation.update(state)
    }

    override fun clear() {
        shownState = null
        animation.clear()
    }

    override fun stop() {
        shownState = null
        animation.stop()
    }

    context(state: State)
    private fun render() = verticalLayout {
        printlnOnDebug { "Updating UI ..." }

        align = TextAlign.LEFT

        var additionalRows = 0

        val top = renderTitle(
            directory = state.directory,
            filter = state.filter,
            showCursor = !state.isTypingCommand && !state.inQuickMacroMode
        )
        additionalRows += 1

        val bottom = renderBottom { additionalRows += it }

        val table = renderTable(
            entries = state.filteredItems,
            cursor = state.cursor,
            filter = state.filter,
            additionalRows = additionalRows
        )

        cell(top)
        cell(table)
        cell(bottom)
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
                    row { cell(Text(styles.nameDecorations("No results …"))) }
                    additionalRows += 1
                } else {
                    row { cell(Text(styles.nameDecorations("There is nothing here"))) }
                    additionalRows += 1
                }
            }
            return@table
        }

        header {
            row {
                config.shownColumns.forEach { column ->
                    cell(column.title)
                }
                cell(styles.nameHeader("${unselectedNamePrefix}Name"))
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
                            style = styles.nameDecorations
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
        val style = styles.filter + TextStyles.bold
        return buildString {
            append(" ${styles.path("$RealSystemPathSeparator")} ")
            append(style(filter))
            if (showCursor) append(style("_"))
        }
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun renderName(entry: Entry, isSelected: Boolean, filter: String): String {
        val filterMarkerStyle = styles.filterMarker + TextStyles.bold
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
            .dressUpEntryName(entry, isSelected = isSelected, showLinkTarget = true)
            .let {
                when (isSelected) {
                    true -> "${styles.path(selectedNamePrefix)}$it"
                    false -> "${styles.nameDecorations(unselectedNamePrefix)}$it"
                }
            }
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

        val style = styles.path
        return style(shortened.joinToString(" $RealSystemPathSeparator ")).let {
            if (debugMode) "$path\n$it" else it
        }
    }

    context(state: State)
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
                cell("${styles.genericElements("•")} ${renderMenuHints()}")
                collectAdditionalRows(1)
            }
        }
    }

    context(state: State)
    private fun renderMenu(
        collectAdditionalRows: (Int) -> Unit
    ) = grid {
        state.shownMenuActions.forEachIndexed { i, item ->
            row {
                cell(styles.genericElements("│"))
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
        collectAdditionalRows(state.shownMenuActions.size)
    }

    context(state: State)
    private fun renderNavHints() = buildHints<State> {
        if (state.inQuickMacroMode) {
            val name = when (state.currentEntry?.type) {
                Directory -> "dir"
                RegularFile -> "file"
                SymbolicLink -> "link"
                Unknown -> "entry"
                null -> "macro"
            }
            add { state.currentEntry.style(name) }
            addSpacing { styles.genericElements(" │ ") }
            addAction(actions.cancelQuickMacroMode)
            val availableMacros = actions.quickMacroModeMacroActions.filter { it.isAvailable() }
            availableMacros.forEach {
                addAction(it)
            }
            when {
                actions.quickMacroModeMacroActions.isEmpty() -> add { styles.keyHintLabels("No macros defined") }
                availableMacros.isEmpty() -> add { styles.keyHintLabels("No macros available") }
            }
        } else {
            addAction(actions.navigateUp)

            addAction(actions.cursorUp, weakSpacing = true)
            addAction(actions.cursorDown, weakSpacing = true)

            addAction(actions.navigateInto)
            addAction(actions.navigateOpen)

            addAction(actions.autocompleteFilter)
            addAction(actions.clearFilter)

            addAction(actions.discardCommand)

            addAction(actions.exitCD)
            addAction(actions.exit)

            addAction(actions.openMenu)
            addAction(actions.exitMenu)

            actions.macroActions.forEach { action ->
                addAction(action)
            }
        }

        if (debugMode) {
            addSpacing(weak = true)
            if (state.inQuickMacroMode) {
                add { debugStyle("M") }
            }
            state.lastReceivedEvent?.let { lastReceivedEvent ->
                add { debugStyle("Key: ${lastReceivedEvent.prettyName}") }
            }
        }
    }

    context(state: State)
    private fun renderMenuHints() = buildHints<State> {
        addAction(actions.closeMenu)
        addAction(actions.menuUp, weakSpacing = true)
        addAction(actions.menuDown, weakSpacing = true)
        if (listOf(actions.closeMenu, actions.menuUp).any { it.isShown() }) {
            add(weakSpacing = true) { styles.keyHintLabels("navigate") }
        }
    }

    private val debugStyle: TextStyle by lazy { TextColors.magenta }

    companion object {
        context(context: FullContext)
        private fun String.dressUpEntryName(entry: Entry, isSelected: Boolean, showLinkTarget: Boolean = false): String {
            fun common(string: String): String = when {
                !isSelected && entry.isHidden == true -> TextStyles.dim(string)
                else -> string
            }
            return when (entry.type) {
                else if entry.error != null -> "${common(context.styles.nameDecorations(this))} "
                SymbolicLink -> when (showLinkTarget) {
                    true -> {
                        val linkTarget = entry.linkTarget
                        val renderedLinkTarget = linkTarget?.path?.toString()?.dressUpEntryName(
                            linkTarget.targetEntry,
                            isSelected = isSelected,
                            showLinkTarget = false
                        ) ?: "${context.styles.nameDecorations("?")} "
                        "${common("${context.styles.link(this)} ${context.styles.nameDecorations("->")}")} $renderedLinkTarget"
                    }

                    false -> "${common(context.styles.link(this))} "
                }

                Directory -> "${common("${context.styles.directory(this)}${context.styles.nameDecorations("$RealSystemPathSeparator")}")} "
                RegularFile -> "${common(context.styles.file(this))} "
                Unknown -> "${common(context.styles.nameDecorations(this))} "
            }
        }
    }
}
