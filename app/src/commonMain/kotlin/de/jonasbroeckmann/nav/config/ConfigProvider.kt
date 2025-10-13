package de.jonasbroeckmann.nav.config

interface ConfigProvider {
    val config: Config
}

context(configProvider: ConfigProvider)
val config get() = configProvider.config
