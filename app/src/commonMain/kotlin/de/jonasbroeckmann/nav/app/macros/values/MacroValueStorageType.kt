package de.jonasbroeckmann.nav.app.macros.values

sealed class MacroValueStorageType(open val key: String) {
    override fun toString() = key

    data object Local : MacroValueStorageType("local")
    data object Session : MacroValueStorageType("session")
    data object Persistent : MacroValueStorageType("persistent")
    data object SharedLocal : MacroValueStorageType("shared_local")
    data object SharedSession : MacroValueStorageType("shared_session")
    data object SharedPersistent : MacroValueStorageType("shared_persistent")
    data object Property : MacroValueStorageType("property")
    data object Environment : MacroValueStorageType("env")
    data class Custom(override val key: String) : MacroValueStorageType(key)
    companion object {
        operator fun invoke(key: String) = when (key) {
            Local.key -> Local
            Session.key -> Session
            Persistent.key -> Persistent
            SharedLocal.key -> SharedLocal
            SharedSession.key -> SharedSession
            SharedPersistent.key -> SharedPersistent
            Property.key -> Property
            Environment.key -> Environment
            else -> Custom(key)
        }

        val MacroValueStorageType?.isLocal get() = this is Local?
    }
}
