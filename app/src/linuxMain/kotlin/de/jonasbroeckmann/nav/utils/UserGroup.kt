package de.jonasbroeckmann.nav.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.toKString
import platform.posix.getgrgid
import platform.posix.getpwuid

@OptIn(ExperimentalForeignApi::class)
fun getUserNameFromId(uid: UInt): String? = getpwuid(uid)?.pointed?.pw_name?.toKString()

@OptIn(ExperimentalForeignApi::class)
fun getGroupNameFromId(gid: UInt): String? = getgrgid(gid)?.pointed?.gr_name?.toKString()
