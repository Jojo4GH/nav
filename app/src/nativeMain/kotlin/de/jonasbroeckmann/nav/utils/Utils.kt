package de.jonasbroeckmann.nav.utils

actual fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
