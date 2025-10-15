package de.jonasbroeckmann.nav.config

interface StylesProvider {
    val styles: Styles
}

context(stylesProvider: StylesProvider)
val styles get() = stylesProvider.styles
