package org.example

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val platform: String = "${Platform.osFamily} on ${Platform.cpuArchitecture}"
