package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.utils.sink
import de.jonasbroeckmann.nav.framework.utils.source
import de.jonasbroeckmann.nav.infoOnDebug
import kotlinx.io.files.Path
import kotlinx.io.okio.asOkioSink
import kotlinx.io.okio.asOkioSource
import okio.buffer
import okio.use

class YamlFileMacroValueStorage(
    private val file: Path,
    logger: Logger
) : MutableMacroValueStorageBase(logger) {
    private var cached: MacroValue.Dictionary? = null

    override fun value(): MacroValue.Dictionary {
        var cached = this.cached
        if (cached == null) {
            logger.infoOnDebug { "Loading values from $file" }
            cached = file.source().asOkioSource().use { Config.Yaml.decodeFromSource<MacroValue.Dictionary>(it) }
            this.cached = cached
        }
        return cached
    }

    override fun update(updater: MacroValue.Dictionary.() -> MacroValue.Dictionary) {
        val old = value()
        cached = old.updater()
        if (cached != old) {
            logger.infoOnDebug { "Updating values in $file" }
            file.sink().asOkioSink().buffer().use {
                Config.Yaml.encodeToBufferedSink(cached, it)
            }
        }
    }

    companion object {
        context(logger: Logger)
        operator fun invoke(file: Path) = YamlFileMacroValueStorage(file, logger)
    }
}
