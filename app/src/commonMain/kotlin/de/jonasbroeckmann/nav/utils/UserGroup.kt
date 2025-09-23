package de.jonasbroeckmann.nav.utils

import de.jonasbroeckmann.nav.app.State

data class UserGroup(
    val userName: String?,
    val groupName: String?,
) {
    companion object {
        val None = UserGroup(
            userName = null,
            groupName = null,
        )
    }
}

expect fun State.Entry.getUserGroup(): UserGroup
