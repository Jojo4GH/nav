package de.jonasbroeckmann.nav.app.macros.values

sealed class MacroValueStorageType(open val key: String) {
    override fun toString() = key

    data object PrivateLocal : MacroValueStorageType("local")
    data object PrivateSession : MacroValueStorageType("session")
    data object PrivatePersistent : MacroValueStorageType("persistent")
    data object SharedLocal : MacroValueStorageType("local_shared")
    data object SharedSession : MacroValueStorageType("session_shared")
    data object SharedPersistent : MacroValueStorageType("persistent_shared")
    data object Property : MacroValueStorageType("property")
    data object Environment : MacroValueStorageType("env")
    data class Custom(override val key: String) : MacroValueStorageType(key)
    companion object {
        operator fun invoke(key: String) = when (key) {
            PrivateLocal.key -> PrivateLocal
            PrivateSession.key -> PrivateSession
            PrivatePersistent.key -> PrivatePersistent
            SharedLocal.key -> SharedLocal
            SharedSession.key -> SharedSession
            SharedPersistent.key -> SharedPersistent
            Property.key -> Property
            Environment.key -> Environment
            else -> Custom(key)
        }

        val MacroValueStorageType?.isLocal get() = this is PrivateLocal?
    }
}
