package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.utils.sink
import de.jonasbroeckmann.nav.framework.utils.source
import kotlinx.io.files.Path
import kotlinx.io.okio.asOkioSink
import kotlinx.io.okio.asOkioSource
import okio.buffer
import okio.use

class YamlFileMacroValueStorage(
    private val file: Path
) : MutableMacroValueStorageBase() {
    private var cached: MacroValue? = null

    override fun get(): MacroValue {
        var cached = this.cached
        if (cached == null) {
            cached = file.source().asOkioSource().use { Config.Yaml.decodeFromSource<MacroValue>(it) }
            this.cached = cached
        }
        return cached
    }

    override fun update(updater: MacroValue.() -> MacroValue) {
        val old = get()
        cached = old.updater()
        if (cached != old) {
            file.sink().asOkioSink().buffer().use {
                Config.Yaml.encodeToBufferedSink(cached, it)
            }
        }
    }
}
