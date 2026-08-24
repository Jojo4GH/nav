package de.jonasbroeckmann.nav.utils

expect object EnvironmentVariables {
    operator fun get(key: String): String?

    fun get(): Map<String, String>

    operator fun set(key: String, value: String?): Boolean
}
