package de.jonasbroeckmann.nav.config

import kotlinx.io.files.Path

interface ConfigProvider {
    val config: Config
    val configPath: Path?

    companion object {
        operator fun invoke(config: Config, configPath: Path?) = object : ConfigProvider {
            override val config get() = config
            override val configPath get() = configPath
        }
    }
}

context(configProvider: ConfigProvider)
val config get() = configProvider.config
