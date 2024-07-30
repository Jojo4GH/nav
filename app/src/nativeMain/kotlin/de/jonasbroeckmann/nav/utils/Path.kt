package de.jonasbroeckmann.nav.utils

import kotlin.experimental.ExperimentalNativeApi


@OptIn(ExperimentalNativeApi::class)
actual val PathsSeparator: Char = when (Platform.osFamily) {
    OsFamily.WINDOWS -> ';'
    else -> ':'
}

@OptIn(ExperimentalNativeApi::class)
actual val RealSystemPathSeparator: Char = when (Platform.osFamily) {
    OsFamily.WINDOWS -> '\\'
    else -> '/'
}
