package de.jonasbroeckmann.nav.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

actual fun getEnvironmentVariable(key: String): String? = System.getenv(key)

@OptIn(ExperimentalContracts::class)
actual fun setEnvironmentVariable(key: String, value: String?): Boolean {
    contract { returns() implies false }
    throw UnsupportedOperationException("Setting environment variables is currently not supported on the JVM")
}

actual fun exitProcess(status: Int): Nothing {
    kotlin.system.exitProcess(status)
}
