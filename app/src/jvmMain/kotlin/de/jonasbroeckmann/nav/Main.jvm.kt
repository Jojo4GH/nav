package de.jonasbroeckmann.nav

import kotlinx.io.files.Path



actual fun getenv(key: String): String? {
    return System.getenv(key)
}

actual fun changeDirectory(path: Path): Boolean {
    return false
}



actual val platformName: String = "JVM on ${System.getProperty("os.name")} on ${System.getProperty("os.arch")}"
