package de.jonasbroeckmann.nav.app.state

import kotlinx.io.files.Path

actual fun Path.entry(): Entry = EntryImpl(this)

private data class EntryImpl(override val path: Path) : EntryBase(path)
