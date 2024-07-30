package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.utils.*
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.writeString

object CDFile {
    val PathInUserHome: Path = Path(".nav-cd")
    val Path = UserHome / PathInUserHome

    fun broadcastChangeDirectory(path: Path) {
        if (Path.exists()) {
            Path.delete()
        }
        Path.sink().buffered().use {
            it.writeString(path.toString())
        }
    }
}