package de.jonasbroeckmann.nav.update

internal expect fun GitHubRelease.findAssetForCurrentPlatform(): GitHubReleaseAsset?

internal fun Sequence<GitHubReleaseAsset>.filterArchives() = filter {
    it.name.endsWith(".zip") || it.name.endsWith(".tar.gz")
}
