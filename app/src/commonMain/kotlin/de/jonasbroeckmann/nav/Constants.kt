package de.jonasbroeckmann.nav

import de.jonasbroeckmann.nav.app.BuildConfig

object Constants {
    val BinaryName get() = BuildConfig.BINARY_NAME

    val RepositoryUrl get() = "https://github.com/Jojo4GH/nav"
    val IssuesUrl get() = "$RepositoryUrl/issues"
}
