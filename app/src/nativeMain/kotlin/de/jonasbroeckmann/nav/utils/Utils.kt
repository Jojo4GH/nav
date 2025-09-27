package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(key: String): String? = platform.posix.getenv(key)?.toKString()

actual fun exitProcess(status: Int): Nothing = kotlin.system.exitProcess(status)
