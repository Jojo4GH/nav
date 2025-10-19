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

//context(context: FullContext)
//fun CoroutineScope.startDownloadProgressBar(
//    text: String
//): ProgressTask<Unit> {
//    val progress = progressBarLayout(textFps = 30, animationFps = 30) {
//        text { text }
//        completed()
//        speed("B/s")
//        timeRemaining()
//        cell { if (isFinished) Text(styles.success("✓")) else EmptyWidget }
//    }.animateInCoroutine(context.terminal)
//    launch { progress.execute() }
//    return progress
//}
//
//context(context: FullContext)
//suspend fun performUpdateFromRelease(release: GitHubRelease) = coroutineScope {
//    val asset = release.findAssetForCurrentPlatform()
//        ?: error("Could not find asset for current platform in release ${release.tagName}")
//
//    withTemporaryDirectory { tempDir ->
//        val assetDestination = tempDir / asset.name
//
//        val downloadProgress = startDownloadProgressBar("Download")
//        downloadProgress.update { total = asset.size.toLong() }
//        val totalDownloaded = download(
//            url = asset.browserDownloadUrl,
//            destination = assetDestination,
//            onProgress = { downloadProgress.update(it) },
//        )
//        downloadProgress.update {
//            completed = totalDownloaded
//            total = totalDownloaded
//        }
//
//
//
//    }
//}
//
//suspend fun download(url: String, destination: Path, onProgress: (Long) -> Unit): Long {
//    client.get(url).bodyAsChannel().asSource().buffered().use { input ->
//        destination.rawSink().buffered().use { output ->
//            var total = 0L
//            try {
//                val stepSize = 8 * 1024L
//                while (true) {
//                    input.readTo(output, stepSize)
//                    total += stepSize
//                    onProgress(total)
//                }
//            } catch (_: kotlinx.io.EOFException) {
//                /* no-op */
//            }
//            return total
//        }
//    }
//}
