package de.jonasbroeckmann.nav.update

internal actual fun GitHubRelease.findAssetForCurrentPlatform(): GitHubReleaseAsset? = assets.asSequence()
    .filterArchives()
    .filter { "windows" in it.name }
    .filter { "x86_64" in it.name }
    .firstOrNull()
