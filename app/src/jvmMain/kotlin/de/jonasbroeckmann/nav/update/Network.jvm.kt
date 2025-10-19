package de.jonasbroeckmann.nav.update

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal actual fun nativeHttpClient(config: HttpClientConfig<*>.() -> Unit) = HttpClient {
    config()
}
