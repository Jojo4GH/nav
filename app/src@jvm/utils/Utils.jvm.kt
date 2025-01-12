package de.jonasbroeckmann.nav.utils


actual fun getenv(key: String): String? {
    return System.getenv(key)
}

actual fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
