package de.jonasbroeckmann.nav.utils

actual object EnvironmentVariables {
    actual operator fun get(key: String): String? = System.getenv(key)

    actual fun get(): Map<String, String> = System.getenv()

    actual operator fun set(key: String, value: String?): Boolean {
        throw UnsupportedOperationException("Setting environment variables is currently not supported on the JVM")
    }
}

actual fun exitProcess(status: Int): Nothing {
    kotlin.system.exitProcess(status)
}
