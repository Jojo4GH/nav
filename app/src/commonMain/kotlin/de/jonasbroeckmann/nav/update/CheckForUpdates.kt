@file:Suppress("detekt:Filename")

package de.jonasbroeckmann.nav.update

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.info
import com.github.ajalt.mordant.widgets.HorizontalRule
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.Text
import com.github.ajalt.mordant.widgets.progress.progressBarLayout
import com.github.ajalt.mordant.widgets.progress.spinner
import com.github.ajalt.mordant.widgets.progress.text
import com.github.ajalt.mordant.widgets.withPadding
import de.jonasbroeckmann.nav.Constants.BinaryName
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.printlnOnDebug
import de.jonasbroeckmann.nav.utils.executeWhile
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

context(context: PartialContext)
suspend fun checkForUpdates(): CheckForUpdatesResult {
    val url = "https://api.github.com/repos/Jojo4GH/nav/releases"

    context.printlnOnDebug { "Fetching releases from '$url' ..." }
    val response = try {
        Network.client.get(url) {
            accept(ContentType("application", "vnd.github+json"))
        }
    } catch (e: Exception) {
        return CheckForUpdatesResult.Error("While fetching releases: ${e.message}")
    }
    if (!response.status.isSuccess()) {
        return CheckForUpdatesResult.Error("Fetching releases failed: ${response.status}")
    }

    context.printlnOnDebug { "Parsing releases from response ..." }
    val allReleases = try {
        response.body<List<GitHubRelease>>()
    } catch (e: Exception) {
        return CheckForUpdatesResult.Error("While parsing releases: ${e.message}")
    }
    context.printlnOnDebug { "Found ${allReleases.size} total release(s)" }

    val newerReleases = allReleases
        .asSequence()
        .mapNotNull { release -> release.version?.let { it to release } }
        .let {
            if (Version.Current.isStable) {
                it.filter { (version) -> version.isStable }
            } else {
                it
            }
        }
        .filter { (version) -> version > Version.Current }
        .sortedByDescending { (version) -> version }
        .toList()
    context.printlnOnDebug { "Found ${newerReleases.size} newer release(s)" }

    if (newerReleases.isEmpty()) {
        return CheckForUpdatesResult.NoUpdates
    }
    return CheckForUpdatesResult.UpdateAvailable(newerReleases)
}

context(context: PartialContext)
suspend fun checkForUpdatesAnimated() = coroutineScope {
    progressBarLayout(spacing = 1) {
        spinner(Spinner.Dots())
        text { "Checking for updates" }
    }.animateInCoroutine(
        context.terminal,
        clearWhenFinished = true
    ).executeWhile {
        checkForUpdates()
    }
}

sealed interface CheckForUpdatesResult {
    data object NoUpdates : CheckForUpdatesResult

    data class UpdateAvailable(
        val newerReleases: List<Pair<Version, GitHubRelease>>
    ) : CheckForUpdatesResult {
        @OptIn(ExperimentalTime::class)
        context(context: PartialContext)
        fun print(includeReleaseNotes: Boolean = true) = with(context.terminal) {
            val (latestVersion, latestRelease) = newerReleases.first()
            info("✦ A new version of $BinaryName is available: $latestVersion (current: ${Version.Current})")

            val releaseTime = (latestRelease.publishedAt ?: latestRelease.createdAt)
            val releaseAge = Clock.System.now() - releaseTime
            info("Released ${releaseAge.ago()}: ${latestRelease.htmlUrl}")

            if (includeReleaseNotes) print(
                verticalLayout {
                    spacing = 1
                    cellsFrom(newerReleases.map { it.buildReleaseNote() })
                }.withPadding {
                    top = 1
                    bottom = 1
                }
            )
        }

        context(terminal: Terminal)
        private fun Pair<Version, GitHubRelease>.buildReleaseNote() = verticalLayout {
            val (version, release) = this@buildReleaseNote
            val titleStyle = terminal.theme.style("style1")
            cell(
                HorizontalRule(
                    title = titleStyle("${release.name ?: version}"),
                    ruleCharacter = "=",
                    ruleStyle = titleStyle
                )
            )
            val body = (release.body ?: release.bodyText)
            if (body != null) {
                cell(
                    Markdown(body).withPadding {
                        if (!body.trimStart().startsWith("#")) {
                            top = 1
                        }
                    }
                )
            } else {
                cell(Text(TextStyles.dim("No release notes")).withPadding { top = 1 })
            }
        }
    }

    data class Error(val message: String) : CheckForUpdatesResult
}

private tailrec fun Duration.ago(suffix: String = "ago"): String {
    if (isNegative()) return (-this).ago(suffix = "from now")
    return toComponents { days, hours, minutes, seconds, nanoseconds ->
        @Suppress("detekt:MagicNumber")
        val milliseconds = nanoseconds / 1_000_000
        when {
            days == 1L -> "1 day $suffix"
            days > 1L -> "$days days $suffix"
            hours == 1 -> "1 hour $suffix"
            hours > 1 -> "$hours hours $suffix"
            minutes == 1 -> "1 minute $suffix"
            minutes > 1 -> "$minutes minutes $suffix"
            seconds == 1 -> "1 second $suffix"
            seconds > 1 -> "$seconds seconds $suffix"
            milliseconds == 1 -> "1 millisecond $suffix"
            milliseconds > 1 -> "$milliseconds milliseconds $suffix"
            else -> "just now"
        }
    }
}
