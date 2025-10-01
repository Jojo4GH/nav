package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.FullContext

interface EntryColumnRenderer {

    context(_: FullContext)
    val title: String

    context(context: FullContext)
    fun render(entry: Entry): Widget
}
