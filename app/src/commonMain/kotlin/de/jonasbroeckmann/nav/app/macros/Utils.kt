package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlMap
import de.jonasbroeckmann.nav.app.StateProvider
import de.jonasbroeckmann.nav.app.state
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.absolute
import de.jonasbroeckmann.nav.utils.div
import de.jonasbroeckmann.nav.utils.metadataOrNull
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path

context(context: PartialContext, stateProvider: StateProvider)
internal fun String.parsePathToDirectoryOrNull(): Path? {
    val path = try {
        Path(this).let { path ->
            if (path.isAbsolute) {
                path
            } else {
                state.directory / path
            }
        }.absolute()
    } catch (_: FileNotFoundException) {
        context.printlnOnDebug { "\"$this\": No such file or directory" }
        return null
    }
    val metadata = path.metadataOrNull()
    if (metadata == null) {
        context.printlnOnDebug { "\"$this\": No such file or directory" }
        return null
    }
    if (!metadata.isDirectory) {
        context.printlnOnDebug { "\"$this\": Not a directory" }
        return null
    }
    return path
}

internal operator fun YamlMap.contains(key: String) = getKey(key) != null
