package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path

actual fun readLink(path: Path): String? {
    //TODO: Just a dummy value. Before doing this need we need to be able to even recognize symlinks under jvm
    return null
}
