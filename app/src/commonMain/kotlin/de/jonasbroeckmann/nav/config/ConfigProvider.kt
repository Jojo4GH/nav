package de.jonasbroeckmann.nav.config

import kotlinx.io.files.Path

interface ConfigProvider {
    val config: Config
    val configPath: Path?
}

context(configProvider: ConfigProvider)
val config get() = configProvider.config
