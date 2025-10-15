package de.jonasbroeckmann.nav.framework.context

import de.jonasbroeckmann.nav.config.Config

interface ConfigProvider {
    val config: Config
}

context(configProvider: ConfigProvider)
val config get() = configProvider.config
