package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.TextStyles
import de.jonasbroeckmann.nav.framework.context.StateProvider
import de.jonasbroeckmann.nav.framework.action.Action
import de.jonasbroeckmann.nav.framework.action.KeyAction
import de.jonasbroeckmann.nav.framework.action.MenuAction
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.framework.context.state
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.state.Entry.Type.Directory
import de.jonasbroeckmann.nav.app.state.Entry.Type.RegularFile
import de.jonasbroeckmann.nav.app.state.Entry.Type.SymbolicLink
import de.jonasbroeckmann.nav.app.state.Entry.Type.Unknown
import de.jonasbroeckmann.nav.framework.context.StylesProvider
import de.jonasbroeckmann.nav.framework.context.styles

context(stylesProvider: StylesProvider)
fun <Context> renderAction(
    action: Action<Context, *, *>,
    context: Context
): String = context(context) {
    val keyStr = when (action) {
        is KeyAction<Context, *> -> action.displayKey()?.let { (styles.keyHints + TextStyles.bold)(it.prettyName) }
        is MenuAction -> null
    }
    val desc = action.description().takeUnless { it.isBlank() }
    val descStr = when (action) {
        is KeyAction<*, *> -> desc?.let { styles.keyHintLabels(it) }
        is MenuAction -> desc
    }
    val str = listOfNotNull(
        keyStr,
        descStr
    ).joinToString(" ")
    action.style()?.let { return it(str) }
    return str
}

@Suppress("detekt:CyclomaticComplexMethod")
val KeyboardEvent.prettyName: String get() {
    var k = when (key) {
        "Enter" -> "enter"
        "Escape" -> "esc"
        "Tab" -> "tab"
        "ArrowUp" -> "↑"
        "ArrowDown" -> "↓"
        "ArrowLeft" -> "←"
        "ArrowRight" -> "→"
        "PageUp" -> "page↑"
        "PageDown" -> "page↓"
        else -> key
    }
    if (alt) k = "alt+$k"
    if (shift && key.length > 1) k = "shift+$k"
    if (ctrl) k = "ctrl+$k"
    return k
}

context(_: StylesProvider)
val Entry?.style get() = when (this?.type) {
    null -> TextColors.magenta
    SymbolicLink -> styles.link
    Directory -> styles.directory
    RegularFile -> styles.file
    Unknown -> styles.nameDecorations
}

context(_: StylesProvider, _: StateProvider)
val Macro.style get() = when {
    dependsOnEntry -> state.currentItem.style
    dependsOnFilter && state.filter.isNotEmpty() -> styles.filter
    else -> TextStyle()
}

fun highlightFilterOccurrences(text: String, filter: String, highlightStyle: TextStyle): String {
    if (filter.isEmpty()) return text
    var index = 0
    var result = ""
    while (index < text.length) {
        val found = text.indexOf(filter, index, ignoreCase = true)
        if (found < 0) {
            result += text.substring(index, text.length)
            break
        }
        result += text.substring(index, found)
        index = found

        result += highlightStyle(text.substring(index, index + filter.length))
        index += filter.length
    }
    return result
}
