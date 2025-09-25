package de.jonasbroeckmann.nav.app

import com.github.ajalt.mordant.rendering.Widget
import de.jonasbroeckmann.nav.ConfigProvider

interface EntryColumnRenderer {
    val title: String

    context(config: ConfigProvider)
    fun render(entry: State.Entry): Widget
}
