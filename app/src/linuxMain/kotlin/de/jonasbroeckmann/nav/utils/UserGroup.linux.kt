package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.getgrgid
import platform.posix.getpwuid

actual fun de.jonasbroeckmann.nav.app.State.Entry.getUserGroup() = UserGroup(
    userName = getUserName(this.stat.userId),
    groupName = getGroupName(this.stat.groupId)
)

@OptIn(ExperimentalForeignApi::class)
private fun getUserName(uid: UInt): String? {
    return getpwuid(uid)?.pointed?.pw_name?.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private fun getGroupName(gid: UInt): String? {
    return getgrgid(gid)?.pointed?.gr_name?.toKString()
}
