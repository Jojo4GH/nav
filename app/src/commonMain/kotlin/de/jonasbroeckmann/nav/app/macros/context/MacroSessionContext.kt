package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.values.EnvironmentMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.YamlFileMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.framework.utils.div


interface MacroSessionContext {
    val sessionStorage: MutableMacroValueStorage
    val persistentStorage: MutableMacroValueStorage?
    val environmentStorage: MutableMacroValueStorage

    companion object {
        context(partialContext: PartialContext, configProvider: ConfigProvider)
        operator fun invoke(): MacroSessionContext = MacroSessionContextImpl(
            partialContext = partialContext,
            configProvider = configProvider
        )
    }
}

private class MacroSessionContextImpl(
    partialContext: PartialContext,
    configProvider: ConfigProvider
) : MacroSessionContext, Logger by partialContext {
    override val sessionStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()
    override val persistentStorage: MutableMacroValueStorage? = run init@{
        val path = (configProvider.configPath ?: Config.findConfigPath(mustExist = false, context = partialContext))
            ?.parent
            ?.let { it / "nav-storage.yaml" }
            ?: run {
                partialContext.warning(
                    """
                    Could not find path for persistent macro storage.
                    Persistent macro storage will be unavailable.
                    """.trimIndent()
                )
                return@init null
            }
        YamlFileMacroValueStorage(path)
    }
    override val environmentStorage: MutableMacroValueStorage = EnvironmentMacroValueStorage()
}
