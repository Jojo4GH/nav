@file:Suppress("detekt:CyclomaticComplexMethod")

package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.*
import com.github.ajalt.mordant.widgets.Padding
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.actions.NormalModeActions
import de.jonasbroeckmann.nav.app.actions.QuickMacroModeActions
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.Entry.Type.*
import de.jonasbroeckmann.nav.app.state.State
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.framework.input.InputMode
import de.jonasbroeckmann.nav.framework.ui.FillLayout
import de.jonasbroeckmann.nav.framework.ui.HintsBuilder
import de.jonasbroeckmann.nav.framework.ui.buildHints
import de.jonasbroeckmann.nav.framework.ui.buildTextFieldContent
import de.jonasbroeckmann.nav.utils.RealSystemPathSeparator
import de.jonasbroeckmann.nav.utils.UserHome
import kotlinx.io.files.Path

context(context: FullContext)
fun buildUI(
    normalModeActions: NormalModeActions,
    quickMacroModeActions: QuickMacroModeActions,
    inputMode: InputMode?,
    state: State,
    dialog: Widget?
): Widget = FillLayout(
    top = {
        buildTitle(
            directory = state.directory,
            maxVisiblePathElements = context.config.maxVisiblePathElements,
            debugMode = context.debugMode,
            filterElement = context(state) {
                val hasFocus = normalModeActions.inputFilter.isAvailable(inputMode)
                if (hasFocus || state.filter.isNotEmpty()) {
                    buildFilter(
                        filter = state.filter,
                        hasFocus = hasFocus
                    )
                } else {
                    null
                }
            }
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
            normalModeActions = normalModeActions,
            quickMacroModeActions = quickMacroModeActions,
            inputMode = inputMode,
            state = state,
            dialog = dialog,
            showHints = !context.config.hideHints,
            debugMode = context.debugMode
        )
    },
    limitToTerminalHeight = context.config.limitToTerminalHeight
)

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
    cellBorders = Borders.LEFT_RIGHT
    tableBorders = Borders.NONE
    borderType = BorderType.BLANK
    padding = Padding(0)
    overflowWrap = OverflowWrap.NORMAL

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

    columns.forEachIndexed { i, _ ->
        column(i) {
            width = Auto
            overflowWrap = NORMAL
        }
    }
    column(columns.size) {
        width = ColumnWidth.Expand()
        overflowWrap = ELLIPSES
    }

    val selectedNamePrefix = if (accessibilityDecorations) "▊" else ""
    val unselectedNamePrefix = if (accessibilityDecorations) " " else ""

    header {
        row {
            columns.forEach { column ->
                cell(column.title) {
                    overflowWrap = NORMAL
                }
            }
            cell(styles.nameHeader("${unselectedNamePrefix}Name")) {
                overflowWrap = NORMAL
            }
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
                    cell(styles.danger(error)) {
                        columnSpan = columns.size
                        align = CENTER
                        overflowWrap = ELLIPSES
                    }
                } else {
                    columns.forEach { column ->
                        cell(column.render(entry))
                    }
                }
                cell(
                    buildName(
                        entry = entry,
                        isSelected = isSelected,
                        filter = filter,
                        selectedNamePrefix = selectedNamePrefix,
                        unselectedNamePrefix = unselectedNamePrefix
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
    maxVisiblePathElements: Int,
    debugMode: Boolean,
    filterElement: String?
): Widget = verticalLayout {
    align = TextAlign.LEFT
    width = ColumnWidth.Expand()
    overflowWrap = ELLIPSES
    cell(buildPathWithFilter(directory, maxVisiblePathElements, debugMode, filterElement),)
}

context(_: StylesProvider)
private fun buildPathWithFilter(
    path: Path,
    maxVisibleElements: Int,
    debugMode: Boolean,
    filterElement: String?
): String {
    fun Path.elements(): List<String> = when (val parent = parent) {
        null if isAbsolute -> emptyList()
        null -> listOf(name)
        else -> parent.elements() + listOf(name)
    }

    var prefix: String? = null
    var elements = path.elements()

    // replace user home with "~" and get prefix
    val userHomeElements = UserHome.elements()
    if (userHomeElements.withIndex().all { (i, it) -> it == elements.getOrNull(i) }) {
        elements = listOf("~") + elements.drop(userHomeElements.size)
    } else if (path.isAbsolute) {
        prefix = "$path".substringBefore(RealSystemPathSeparator)
    }

    // shorten elements, e.g. if maxVisibleElements is 3:
    // [a, b, c] -> [a, b, c]
    // [a, b, c, d] -> [a, …, c, d]
    // [a, b, c, d, e] -> [a, …, d, e]
    if (elements.size > maxVisibleElements) {
        val visibleAtEnd = (maxVisibleElements - 1).coerceAtLeast(1)
        val visibleAtStart = (maxVisibleElements - visibleAtEnd).coerceAtLeast(0)
        elements = elements.take(visibleAtStart) + listOf("…") + elements.takeLast(visibleAtEnd)
    }

    // apply styles to prefix and path
    prefix = prefix?.let { styles.path(it) }
    elements = elements.map { styles.path(it) }

    // append filter
    if (filterElement != null) {
        elements = if (elements.lastOrNull()?.isEmpty() == true) {
            // replace empty last element
            elements.dropLast(1) + listOf(filterElement)
        } else {
            elements + listOf(filterElement)
        }
    }

    // combine everything
    val separator = " $RealSystemPathSeparator "
    return buildString {
        if (prefix != null) {
            append(prefix)
            if (prefix.isEmpty()) {
                append(styles.path(separator.trimStart()))
            } else {
                append(styles.path(separator))
            }
        }
        elements.joinTo(this, styles.path(separator))
    }
        // add debug info
        .let { if (debugMode) "$path  ${path.elements()}\n$it" else it }
}

context(_: StylesProvider)
private fun buildFilter(
    filter: String,
    hasFocus: Boolean
): String = buildTextFieldContent(
    text = filter,
    hasFocus = hasFocus
).let { (styles.filter + TextStyles.bold)(it) }

context(_: StylesProvider)
private fun buildName(
    entry: Entry,
    isSelected: Boolean,
    filter: String,
    selectedNamePrefix: String,
    unselectedNamePrefix: String
): String {
    val filterMarkerStyle = styles.filterMarker + TextStyles.bold
    val selectedStyle = TextStyles.inverse
    return entry.path.name
        .let { highlightFilterOccurrences(it, filter, filterMarkerStyle) }
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
private fun buildBottom(
    normalModeActions: NormalModeActions,
    quickMacroModeActions: QuickMacroModeActions,
    inputMode: InputMode?,
    state: State,
    dialog: Widget?,
    showHints: Boolean,
    debugMode: Boolean
): Widget = verticalLayout {
    align = TextAlign.LEFT
    width = ColumnWidth.Expand()
    overflowWrap = ELLIPSES

    if (dialog != null) {
        cell(dialog)
        return@verticalLayout
    }

    if (showHints) {
        cell(
            buildNavHints(
                normalModeActions = normalModeActions,
                quickMacroModeActions = quickMacroModeActions,
                inputMode = inputMode,
                state = state,
                debugMode = debugMode
            )
        )
    }

    if (state.isMenuOpen) {
        cell(buildMenu(normalModeActions, state))
        if (showHints) {
            cell("${styles.genericElements("•")} ${buildMenuHints(normalModeActions, inputMode, state)}")
        }
    }
}

context(_: StylesProvider)
private fun buildMenu(
    actions: NormalModeActions,
    state: State
) = grid {
    column(0) {
        width = Auto
        overflowWrap = NORMAL
    }
    column(1) {
        width = Auto
        overflowWrap = NORMAL
    }
    column(2) {
        width = ColumnWidth.Expand()
        overflowWrap = ELLIPSES
    }
    state.shownMenuActions.forEachIndexed { i, item ->
        row {
            cell(styles.genericElements("│"))
            val isSelected = i == state.coercedMenuCursor
            if (isSelected) {
                cell(actions.menuSubmit.render(state))
                cell(
                    item.render(state).let { rendered ->
                        item.selectedStyle?.let { it(rendered) + " " } ?: rendered
                    }
                )
            } else {
                cell("")
                cell(item.render(state))
            }
        }
    }
}

context(_: StylesProvider)
private fun buildNavHints(
    normalModeActions: NormalModeActions,
    quickMacroModeActions: QuickMacroModeActions,
    inputMode: InputMode?,
    state: State,
    debugMode: Boolean
) = buildHints(styles.genericElements(" • ")) {
    if (state.inQuickMacroMode) {
        addQuickMacroModeHints(quickMacroModeActions, inputMode, state)
    } else {
        addNormalModeHints(normalModeActions, inputMode, state)
    }

    if (debugMode) {
        addSpacing(weak = true)
        inputMode?.debugLabel?.let {
            add { styles.debugStyle(it) }
        }
        state.lastReceivedEvent?.let { lastReceivedEvent ->
            add { styles.debugStyle("Key: ${lastReceivedEvent.prettyName}") }
        }
    }
}

context(_: StylesProvider)
private fun HintsBuilder.addNormalModeHints(
    actions: NormalModeActions,
    inputMode: InputMode?,
    state: State
) {
    addAction(actions.navigateUp, state, inputMode) { render() }

    addAction(actions.cursorUp, state, inputMode, weakSpacing = true) { render() }
    addAction(actions.cursorDown, state, inputMode, weakSpacing = true) { render() }

    addAction(actions.navigateInto, state, inputMode) { render() }
    addAction(actions.navigateOpen, state, inputMode) { render() }

    addAction(actions.autocompleteFilter, state, inputMode) { render() }
    addAction(actions.clearFilter, state, inputMode) { render() }

    addAction(actions.discardCommand, state, inputMode) { render() }

    addAction(actions.exitCD, state, inputMode) { render() }
    addAction(actions.exit, state, inputMode) { render() }

    addAction(actions.openMenu, state, inputMode) { render() }
    addAction(actions.exitMenu, state, inputMode) { render() }

    actions.normalModeMacroActions.forEach { action ->
        addAction(action, state, inputMode) { render() }
    }
}

context(_: StylesProvider)
private fun HintsBuilder.addQuickMacroModeHints(
    actions: QuickMacroModeActions,
    inputMode: InputMode?,
    state: State
) {
    val name = when (state.currentItem?.type) {
        Directory -> "dir"
        RegularFile -> "file"
        SymbolicLink -> "link"
        Unknown -> "entry"
        null -> "macro"
    }
    add { state.currentItem.style(name) }
    addSpacing { styles.genericElements(" │ ") }
    addAction(actions.cancelQuickMacroMode, state, inputMode) { render() }
    val availableMacros = context(state) {
        actions.quickMacroModeMacroActions.filter { it.isAvailable(inputMode) }
    }
    availableMacros.forEach {
        addAction(it, state, inputMode) { render() }
    }
    when {
        actions.quickMacroModeMacroActions.isEmpty() -> add { styles.keyHintLabels("No macros defined") }
        availableMacros.isEmpty() -> add { styles.keyHintLabels("No macros available") }
    }
}

context(_: StylesProvider)
private fun buildMenuHints(
    actions: NormalModeActions,
    inputMode: InputMode?,
    state: State
) = buildHints(styles.genericElements(" • ")) {
    addAction(actions.closeMenu, state, inputMode) { render() }
    addAction(actions.menuUp, state, inputMode, weakSpacing = true) { render() }
    addAction(actions.menuDown, state, inputMode, weakSpacing = true) { render() }
    val anyNavigationShown = context(state) {
        listOf(actions.closeMenu, actions.menuUp).any { it.isShown(inputMode) }
    }
    if (anyNavigationShown) {
        add(weakSpacing = true) { styles.keyHintLabels("navigate") }
    }
}
