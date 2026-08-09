package de.jonasbroeckmann.nav.app.macros.values

sealed class MacroValueStorageType(open val key: String) {
    override fun toString() = key

    data object Property : MacroValueStorageType("property")
    data object Local : MacroValueStorageType("local")
    data object Session : MacroValueStorageType("session")
    data object Persistent : MacroValueStorageType("persistent")
    data object Environment : MacroValueStorageType("env")
    data class Custom(override val key: String) : MacroValueStorageType(key)
    companion object {
        operator fun invoke(key: String) = when (key) {
            Property.key -> Property
            Local.key -> Local
            Session.key -> Session
            Persistent.key -> Persistent
            Environment.key -> Environment
            else -> Custom(key)
        }

        val MacroValueStorageType?.isLocal get() = this is Local?
    }
}
