package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.MainActions
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.io.files.Path

context(context: FullContext)
fun buildUI(
    actions: MainActions,
    state: State,
    dialog: Widget?
): Widget {
    return FillLayout(
        top = {
            buildTitle(
                directory = state.directory,
                filter = state.filter,
                showCursor = !state.isTypingCommand && !state.inQuickMacroMode,
                maxVisiblePathElements = context.config.maxVisiblePathElements,
                debugMode = context.debugMode
            )
        },
        fill = { availableLines ->
            buildTable(
                entries = state.filteredItems,
                cursor = state.cursor,
                filter = state.filter,
                availableLines = availableLines,
                maxVisibleEntries = context.config.maxVisibleEntries.takeIf { it > 0 },
                accessibilityDecorations = context.accessibilityDecorations,
                columns = context.config.shownColumns
            )
        },
        bottom = {
            buildBottom(
                actions = actions,
                state = state,
                dialog = dialog,
                showHints = !context.config.hideHints,
                debugMode = context.debugMode
            )
        },
        limitToTerminalHeight = context.config.limitToTerminalHeight
    )
}

context(_: FullContext)
private fun buildTable(
    entries: List<Entry>,
    cursor: Int,
    filter: String,
    availableLines: Int?,
    maxVisibleEntries: Int?,
    accessibilityDecorations: Boolean,
    columns: List<EntryColumn>
) = table {
    overflowWrap = OverflowWrap.ELLIPSES
    cellBorders = Borders.LEFT_RIGHT
    tableBorders = Borders.NONE
    borderType = BorderType.BLANK
    padding = Padding(0)

    if (entries.isEmpty()) {
        body {
            if (filter.isNotEmpty()) {
                row { cell(Text(styles.nameDecorations("No results …"))) }
            } else {
                row { cell(Text(styles.nameDecorations("There is nothing here"))) }
            }
        }
        return@table
    }

    val selectedNamePrefix = when {
        accessibilityDecorations -> "▊"
        else -> ""
    }

    val unselectedNamePrefix = when {
        accessibilityDecorations -> " "
        else -> ""
    }

    header {
        row {
            columns.forEach { column ->
                cell(column.title)
            }
            cell(styles.nameHeader("${unselectedNamePrefix}Name"))
        }
    }

    body {
        buildEntries(
            entries = entries,
            cursor = cursor,
            availableLines = availableLines?.let {
                it - 1 // header takes one line
            },
            maxVisibleEntries = maxVisibleEntries,
            renderMore = { n ->
                row {
                    columns.forEach { _ ->
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
                        columnSpan = columns.size
                        align = TextAlign.CENTER
                    }
                } else {
                    columns.forEach { column ->
                        cell(column.render(entry))
                    }
                }
                cell(
                    Text(
                        text = buildName(
                            entry = entry,
                            isSelected = isSelected,
                            filter = filter,
                            selectedNamePrefix = { selectedNamePrefix },
                            unselectedNamePrefix = { unselectedNamePrefix }
                        )
                    )
                )
            }
        }
    }
}

private fun SectionBuilder.buildEntries(
    entries: List<Entry>,
    cursor: Int,
    availableLines: Int?,
    maxVisibleEntries: Int?,
    renderMore: SectionBuilder.(Int) -> Unit,
    renderEntry: SectionBuilder.(Entry, Boolean) -> Unit
) {
    var maxVisible = maxVisibleEntries ?: entries.size
    if (availableLines != null) {
        maxVisible = maxVisible.coerceAtMost(availableLines)
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

context(_: StylesProvider)
private fun buildTitle(
    directory: Path,
    filter: String,
    showCursor: Boolean,
    maxVisiblePathElements: Int,
    debugMode: Boolean
): Widget {
    return Text("${buildPath(directory, maxVisiblePathElements, debugMode)}${buildFilter(filter, showCursor)}")
}

context(_: StylesProvider)
private fun buildFilter(
    filter: String,
    showCursor: Boolean
): String {
    if (filter.isEmpty()) return ""
    val style = styles.filter + TextStyles.bold
    return buildString {
        append(" ${styles.path("$RealSystemPathSeparator")} ")
        append(style(filter))
        if (showCursor) append(style("_"))
    }
}

@Suppress("detekt:CyclomaticComplexMethod")
context(_: StylesProvider)
private fun buildName(
    entry: Entry,
    isSelected: Boolean,
    filter: String,
    selectedNamePrefix: () -> String,
    unselectedNamePrefix: () -> String
): String {
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
                true -> "${styles.path(selectedNamePrefix())}$it"
                false -> "${styles.nameDecorations(unselectedNamePrefix())}$it"
            }
        }
}

context(_: StylesProvider)
private fun String.dressUpEntryName(entry: Entry, isSelected: Boolean, showLinkTarget: Boolean = false): String {
    fun common(string: String): String = when {
        !isSelected && entry.isHidden == true -> TextStyles.dim(string)
        else -> string
    }
    return when (entry.type) {
        else if entry.error != null -> "${common(styles.nameDecorations(this))} "
        SymbolicLink -> when (showLinkTarget) {
            true -> {
                val linkTarget = entry.linkTarget
                val renderedLinkTarget = linkTarget?.path?.toString()?.dressUpEntryName(
                    linkTarget.targetEntry,
                    isSelected = isSelected,
                    showLinkTarget = false
                ) ?: "${styles.nameDecorations("?")} "
                "${common("${styles.link(this)} ${styles.nameDecorations("->")}")} $renderedLinkTarget"
            }

            false -> "${common(styles.link(this))} "
        }

        Directory -> "${common("${styles.directory(this)}${styles.nameDecorations("$RealSystemPathSeparator")}")} "
        RegularFile -> "${common(styles.file(this))} "
        Unknown -> "${common(styles.nameDecorations(this))} "
    }
}

context(_: StylesProvider)
private fun buildPath(
    path: Path,
    maxVisibleElements: Int,
    debugMode: Boolean
): String {
    val pathString = path.toString().let {
        val home = UserHome.toString().removeSuffix("$RealSystemPathSeparator")
        if (it.startsWith(home)) " ~${it.removePrefix(home)}" else it
    }
    val elements = pathString.split(RealSystemPathSeparator)

    val shortened = when {
        elements.size > maxVisibleElements -> {
            elements.subList(0, 1) + listOf("…") + elements.subList(elements.size - (maxVisibleElements - 2), elements.size)
        }
        else -> elements
    }

    val style = styles.path
    return style(shortened.joinToString(" $RealSystemPathSeparator ")).let {
        if (debugMode) "$path\n$it" else it
    }
}

context(_: StylesProvider)
private fun buildBottom(
    actions: MainActions,
    state: State,
    dialog: Widget?,
    showHints: Boolean,
    debugMode: Boolean
): Widget = verticalLayout {
    align = TextAlign.LEFT
    width = ColumnWidth.Expand()

    if (dialog != null) {
        cell(dialog)
        return@verticalLayout
    }

    if (showHints) {
        cell(buildNavHints(actions, state, debugMode))
    }

    if (state.isMenuOpen) {
        cell(buildMenu(actions, state))
        if (showHints) {
            cell("${styles.genericElements("•")} ${buildMenuHints(actions, state)}")
        }
    }
}

context(_: StylesProvider)
private fun buildMenu(
    actions: MainActions,
    state: State
) = grid {
    state.shownMenuActions.forEachIndexed { i, item ->
        row {
            cell(styles.genericElements("│"))
            val isSelected = i == state.coercedMenuCursor
            if (isSelected) {
                cell(renderAction(actions.menuSubmit, state))
                cell(
                    renderAction(item, state).let { rendered ->
                        item.selectedStyle?.let { it(rendered) + " " } ?: rendered
                    }
                )
            } else {
                cell("")
                cell(renderAction(item, state))
            }
        }
    }
}

context(_: StylesProvider)
private fun buildNavHints(
    actions: MainActions,
    state: State,
    debugMode: Boolean
) = buildHints {
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
        addAction(actions.cancelQuickMacroMode, state)
        val availableMacros = context(state) {
            actions.quickMacroModeMacroActions.filter { it.isAvailable() }
        }
        availableMacros.forEach {
            addAction(it, state)
        }
        when {
            actions.quickMacroModeMacroActions.isEmpty() -> add { styles.keyHintLabels("No macros defined") }
            availableMacros.isEmpty() -> add { styles.keyHintLabels("No macros available") }
        }
    } else {
        addAction(actions.navigateUp, state)

        addAction(actions.cursorUp, state, weakSpacing = true)
        addAction(actions.cursorDown, state, weakSpacing = true)

        addAction(actions.navigateInto, state)
        addAction(actions.navigateOpen, state)

        addAction(actions.autocompleteFilter, state)
        addAction(actions.clearFilter, state)

        addAction(actions.discardCommand, state)

        addAction(actions.exitCD, state)
        addAction(actions.exit, state)

        addAction(actions.openMenu, state)
        addAction(actions.exitMenu, state)

        actions.normalModeMacroActions.forEach { action ->
            addAction(action, state)
        }
    }

    if (debugMode) {
        addSpacing(weak = true)
        if (state.inQuickMacroMode) {
            add { styles.debugStyle("M") }
        }
        state.lastReceivedEvent?.let { lastReceivedEvent ->
            add { styles.debugStyle("Key: ${lastReceivedEvent.prettyName}") }
        }
    }
}

context(_: StylesProvider)
private fun buildMenuHints(
    actions: MainActions,
    state: State
) = buildHints {
    addAction(actions.closeMenu, state)
    addAction(actions.menuUp, state, weakSpacing = true)
    addAction(actions.menuDown, state, weakSpacing = true)
    val anyNavigationShown = context(state) {
        listOf(actions.closeMenu, actions.menuUp).any { it.isShown() }
    }
    if (anyNavigationShown) {
        add(weakSpacing = true) { styles.keyHintLabels("navigate") }
    }
}
