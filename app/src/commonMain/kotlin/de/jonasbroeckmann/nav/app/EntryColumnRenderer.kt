package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.ConfigProvider
import de.jonasbroeckmann.nav.Entry

interface EntryColumnRenderer {
    val title: String

    context(config: ConfigProvider)
    fun render(entry: Entry): Widget
}
