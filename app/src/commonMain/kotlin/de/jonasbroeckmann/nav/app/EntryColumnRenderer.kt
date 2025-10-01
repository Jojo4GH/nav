package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.Entry
import de.jonasbroeckmann.nav.FullContext

interface EntryColumnRenderer {

    context(_: FullContext)
    val title: String

    context(context: FullContext)
    fun render(entry: Entry): Widget
}
