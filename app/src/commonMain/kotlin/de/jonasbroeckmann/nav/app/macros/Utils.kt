package de.jonasbroeckmann.nav.app.macros

import com.charleskorn.kaml.YamlMap
import de.jonasbroeckmann.nav.app.macros.MacroSymbol.Companion.get
import de.jonasbroeckmann.nav.command.Logger
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.framework.utils.div
import de.jonasbroeckmann.nav.framework.utils.metadataOrNull
import kotlinx.io.files.Path

context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
internal fun String.parseToAbsolutePath() = Path(this).let { path ->
    if (path.isAbsolute) {
        path
    } else {
        Path(DefaultMacroProperty.Directory.property.evaluate()?.value.orEmpty()) / path
    }
}

context(scope: MacroEvaluationScope, traceContext: MacroTraceContext)
internal fun String.parseToAbsolutePathToDirectoryOrNull(): Path? {
    val path = parseToAbsolutePath()
    val metadata = path.metadataOrNull()
    if (metadata == null) {
        scope.printlnOnDebug { "\"$this\": No such file or directory" }
        return null
    }
    if (!metadata.isDirectory) {
        scope.printlnOnDebug { "\"$this\": Not a directory" }
        return null
    }
    return path
}

internal operator fun YamlMap.contains(key: String) = getKey(key) != null
