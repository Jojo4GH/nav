package de.jonasbroeckmann.nav.update

import de.jonasbroeckmann.nav.Constants
import de.jonasbroeckmann.nav.app.BuildConfig
import kotlin.jvm.JvmInline
import kotlin.text.get

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<Version> {
    val isStable get() = preRelease == null

    override fun compareTo(other: Version) = when {
        major != other.major -> major - other.major
        minor != other.minor -> minor - other.minor
        patch != other.patch -> patch - other.patch
        preRelease != other.preRelease -> when {
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
        else -> 0
    }

    override fun toString() = buildString {
        append("$major.$minor.$patch")
        if (preRelease != null) {
            append("-$preRelease")
        }
    }

    companion object {
        private val regex = Regex("""(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(-(?<preRelease>.+))?""")

        fun of(version: String): Version? {
            val match = regex.matchEntire(version) ?: return null
            return Version(
                major = match.groups["major"]!!.value.toInt(),
                minor = match.groups["minor"]!!.value.toInt(),
                patch = match.groups["patch"]!!.value.toInt(),
                preRelease = match.groups["preRelease"]?.value
            )
        }

        val Current by lazy {
            of(Constants.Version) ?: throw AssertionError("Could not parse current version '${BuildConfig.VERSION}'")
        }
    }
}
