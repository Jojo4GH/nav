package de.jonasbroeckmann.nav.update

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object Network {
    internal val json by lazy {
        Json {
            isLenient = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    internal val client by lazy {
        nativeHttpClient {
            followRedirects = true
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}

internal expect fun nativeHttpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient
