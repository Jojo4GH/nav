package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlMap
import de.jonasbroeckmann.nav.framework.context.StateProvider
import de.jonasbroeckmann.nav.framework.context.state
import de.jonasbroeckmann.nav.framework.context.PartialContext
import de.jonasbroeckmann.nav.framework.context.printlnOnDebug
import de.jonasbroeckmann.nav.utils.div
import de.jonasbroeckmann.nav.utils.metadataOrNull
import kotlinx.io.files.Path

context(stateProvider: StateProvider)
internal fun String.parseAbsolutePath() = Path(this).let { path ->
    if (path.isAbsolute) {
        path
    } else {
        state.directory / path
    }
}

context(context: PartialContext, stateProvider: StateProvider)
internal fun String.parseAbsolutePathToDirectoryOrNull(): Path? {
    val path = parseAbsolutePath()
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
