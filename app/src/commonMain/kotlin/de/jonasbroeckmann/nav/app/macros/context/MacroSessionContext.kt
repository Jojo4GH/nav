package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Companion.plus
import de.jonasbroeckmann.nav.app.macros.values.*
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.framework.utils.div

interface MacroSessionContext {
    val sharedSessionStorage: MutableMacroValueStorage
    val sharedPersistentStorage: MutableMacroValueStorage?
    val environmentStorage: MutableMacroValueStorage

    fun privateSessionStorage(id: Macro.Id): MutableMacroValueStorage

    fun privatePersistentStorage(id: Macro.Id): MutableMacroValueStorage?

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
    private val sessionStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()
    private val persistentStorage: MutableMacroValueStorage? = run init@{
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

    override val sharedSessionStorage by lazy { sessionStorage.asSharedStorage() }
    override val sharedPersistentStorage by lazy { persistentStorage?.asSharedStorage() }

    override fun privateSessionStorage(id: Macro.Id) = sessionStorage.asPrivateStorage(id)

    override fun privatePersistentStorage(id: Macro.Id) = persistentStorage?.asPrivateStorage(id)

    companion object {
        private fun MutableMacroValueStorage.asSharedStorage() = MutableMacroValueStorageView(this, "shared")

        private fun MutableMacroValueStorage.asPrivateStorage(id: Macro.Id) = MutableMacroValueStorageView(this, "$id")
    }
}

private class MutableMacroValueStorageView(
    private val baseStorage: MutableMacroValueStorage,
    private val key: String
) : MutableMacroValueStorage {
    private fun MacroPathExpression.transformed() = MacroPathExpression.Operator.Key(this@MutableMacroValueStorageView.key) + this

    override fun value(): MacroValue.Dictionary {
        val value = baseStorage.value()[key] ?: return MacroValue.Dictionary()
        when (value) {
            is MacroValue.Dictionary -> return value
            is MacroValue.Array, is MacroValue.Text -> throw IllegalStateException(
                "Cannot construct dictionary view at '$key' in base storage: Got ${value.type.name}"
            )
        }
    }

    override fun get(path: MacroPathExpression): MacroValue? = baseStorage[path.transformed()]

    override operator fun contains(path: MacroPathExpression): Boolean = path.transformed() in baseStorage

    override fun set(
        path: MacroPathExpression,
        newValue: MacroValue?
    ) {
        baseStorage[path.transformed()] = newValue
    }
}
