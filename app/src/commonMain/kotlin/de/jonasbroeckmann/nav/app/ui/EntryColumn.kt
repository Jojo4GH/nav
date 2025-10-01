package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.app.state.Entry
import de.jonasbroeckmann.nav.app.FullContext
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.MonthNames
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Suppress("unused", "detekt:Wrapping")
@Serializable(with = EntryColumn.Companion::class)
enum class EntryColumn(
    title: FullContext.() -> String,
    render: FullContext.(Entry) -> Widget
) : EntryColumnRenderer by object : EntryColumnRenderer {

    context(context: FullContext)
    override val title get() = context.title()

    context(context: FullContext)
    override fun render(entry: Entry): Widget = context.render(entry)
} {
    Permissions({ styles.permissionHeader("Permissions") }, { entry ->
        val styleRead = styles.permissionRead
        val styleWrite = styles.permissionWrite
        val styleExecute = styles.permissionExecute

        fun render(perm: Entry.Permissions?): String {
            val r = if (perm?.canRead == true) styleRead("r") else (styleRead + TextStyles.dim)("-")
            val w = if (perm?.canWrite == true) styleWrite("w") else (styleWrite + TextStyles.dim)("-")
            val x = if (perm?.canExecute == true) styleExecute("x") else (styleExecute + TextStyles.dim)("-")
            return "$r$w$x"
        }

        Text("${render(entry.userPermissions)}${render(entry.groupPermissions)}${render(entry.othersPermissions)}")
    }),

    HardLinkCount({ styles.hardlinkCountHeader("#HL") }, { entry ->
        Text(styles.hardlinkCount("${entry.hardlinkCount}"))
    }),

    UserName({ styles.userHeader("User") }, { entry ->
        Text(entry.userName?.let { styles.user(it) } ?: (styles.user + TextStyles.dim)("?"))
    }),

    GroupName({ styles.groupHeader("Group") }, { entry ->
        Text(entry.groupName?.let { styles.group(it) } ?: (styles.user + TextStyles.dim)("?"))
    }),

    @Suppress("detekt:MagicNumber")
    EntrySize({ styles.entrySizeHeader("Size") }, render@{ entry ->
        val bytes = entry.size ?: return@render Text("", align = TextAlign.RIGHT)

        val numStyle = styles.entrySize
        val unitStyle = numStyle + TextStyles.dim

        val units = listOf("k", "M", "G", "T", "P")

        if (bytes < 1000) {
            return@render Text(numStyle("$bytes"), align = TextAlign.RIGHT)
        }

        var value = bytes / 1000.0
        var i = 0
        while (value >= 1000 && i + 1 < units.size) {
            value /= 1000.0
            i++
        }

        fun Double.format(): String {
            toString().take(3).let {
                if (it.endsWith('.')) return it.dropLast(1)
                return it
            }
        }

        Text(
            "${numStyle(value.format())}${unitStyle(units[i])}",
            align = TextAlign.RIGHT
        )
    }),

    @Suppress("detekt:MagicNumber")
    @OptIn(ExperimentalTime::class)
    LastModified({ styles.modificationTimeHeader("Last Modified") }, { entry ->
        val instant = entry.lastModificationTime ?: Instant.fromEpochMilliseconds(0L)
        val now = Clock.System.now()
        val duration = now - instant
        val format = if (duration.absoluteValue > 365.days) DateTimeComponents.Format {
            day()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars("  ")
            year()
        } else DateTimeComponents.Format {
            day()
            chars(" ")
            monthName(MonthNames.ENGLISH_ABBREVIATED)
            chars(" ")
            hour()
            chars(":")
            minute()
        }

        val hoursSinceInstant = (duration.inWholeMinutes / 60.0).coerceAtLeast(0.0)
        val factor = 2.0.pow(-hoursSinceInstant / config.modificationTime.halfBrightnessAtHours)

        val minimumBrightness = config.modificationTime.minimumBrightness.let {
            if (accessibilitySimpleColors) it.coerceAtLeast(0.5) else it
        }
        val brightnessRange = minimumBrightness..1.0
        val brightness = factor * (brightnessRange.endInclusive - brightnessRange.start) + brightnessRange.start

        val rgb = (styles.modificationTime.color ?: TextColors.white).toSRGB()
        val style = TextColors.color(rgb.toHSV().copy(v = brightness.toFloat()))
        Text(style(instant.format(format)))
    });

    companion object : KSerializer<EntryColumn> {
        override val descriptor = PrimitiveSerialDescriptor(EntryColumn::class.simpleName!!, PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: EntryColumn) {
            encoder.encodeString(value.name)
        }

        override fun deserialize(decoder: Decoder): EntryColumn {
            return valueOf(decoder.decodeString())
        }
    }
}
