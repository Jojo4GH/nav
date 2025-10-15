package de.jonasbroeckmann.nav.framework.context

import de.jonasbroeckmann.nav.config.Styles

interface StylesProvider {
    val styles: Styles
}

context(stylesProvider: StylesProvider)
val styles get() = stylesProvider.styles
