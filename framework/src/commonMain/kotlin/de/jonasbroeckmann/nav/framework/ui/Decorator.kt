package de.jonasbroeckmann.nav.framework.ui

import com.github.ajalt.mordant.rendering.Widget

fun interface Decorator : (Widget) -> Widget
