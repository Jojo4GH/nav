package de.jonasbroeckmann.nav.utils

import kotlinx.io.files.Path

expect fun readLink(path: Path): String?
