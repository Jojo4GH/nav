package de.jonasbroeckmann.nav

import kotlinx.io.files.Path

actual fun Path.entry(): Entry = EntryImpl(this)

private data class EntryImpl(override val path: Path) : EntryBase(path)
