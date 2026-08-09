package de.jonasbroeckmann.nav.app.macros.context

import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.macros.values.EnvironmentMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MutableMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.YamlFileMacroValueStorage
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.utils.div

class MacroSessionContext(
    context: FullContext,
    stateProvider: StateProvider
) : FullContext by context, StateProvider by stateProvider {
    val sessionStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()
    val persistentStorage: MutableMacroValueStorage? = run init@{
        val path = (configPath ?: Config.findConfigPath(mustExist = false))
            ?.parent
            ?.let { it / "nav-storage.yaml" }
            ?: run {
                terminal.warning(
                    """
                    Could not find path for persistent macro storage.
                    Persistent macro storage will be unavailable.
                    """.trimIndent()
                )
                return@init null
            }
        YamlFileMacroValueStorage(path, logger = context)
    }
    val environmentStorage: MutableMacroValueStorage = EnvironmentMacroValueStorage(logger = context)

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        operator fun invoke() = MacroSessionContext(context, stateProvider)
    }
}
