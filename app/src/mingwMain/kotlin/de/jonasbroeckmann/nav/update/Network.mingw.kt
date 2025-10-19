package de.jonasbroeckmann.nav.update

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.winhttp.WinHttp

internal actual fun nativeHttpClient(config: HttpClientConfig<*>.() -> Unit) = HttpClient(WinHttp) {
    config()
}
