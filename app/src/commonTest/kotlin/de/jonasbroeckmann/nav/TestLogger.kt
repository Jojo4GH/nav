package de.jonasbroeckmann.nav

class TestLogger(override val debugMode: Boolean = true) : Logger {
    override fun println(message: Any?) = kotlin.io.println(message)

    override fun info(message: Any?) = println("INFO: $message")

    override fun success(message: Any?) = println("SUCCESS: $message")

    override fun warning(message: Any?) = println("WARNING: $message")

    override fun danger(message: Any?) = println("DANGER: $message")
}
