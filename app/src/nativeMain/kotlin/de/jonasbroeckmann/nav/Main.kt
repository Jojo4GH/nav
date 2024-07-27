package de.jonasbroeckmann.nav

import kotlin.experimental.ExperimentalNativeApi


@OptIn(ExperimentalNativeApi::class)
actual val platformName: String = "${Platform.osFamily} on ${Platform.cpuArchitecture}"



