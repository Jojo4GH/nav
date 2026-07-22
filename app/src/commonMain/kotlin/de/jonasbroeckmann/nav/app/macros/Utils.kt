package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlMap
import de.jonasbroeckmann.nav.app.macros.MacroSymbol.Companion.get
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.div
import de.jonasbroeckmann.nav.utils.metadataOrNull
import kotlinx.io.files.Path

context(scope: MacroSymbolScope)
internal fun String.parseToAbsolutePath() = Path(this).let { path ->
    if (path.isAbsolute) {
        path
    } else {
        Path(DefaultMacroProperty.Directory.symbol.get()) / path
    }
}

context(context: PartialContext, scope: MacroSymbolScope)
internal fun String.parseToAbsolutePathToDirectoryOrNull(): Path? {
    val path = parseToAbsolutePath()
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
