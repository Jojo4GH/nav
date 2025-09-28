package de.jonasbroeckmann.nav.app

import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.widgets.Text
import de.jonasbroeckmann.nav.ConfigProvider
import de.jonasbroeckmann.nav.Entry
import de.jonasbroeckmann.nav.utils.Stat
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

@Suppress("unused", "detekt:Wrapping")
@Serializable(with = EntryColumn.Companion::class)
enum class EntryColumn(
    title: String,
    render: ConfigProvider.(Entry) -> Widget
) : EntryColumnRenderer by object : EntryColumnRenderer {
    override val title = title

    context(config: ConfigProvider)
    override fun render(entry: Entry): Widget = config.render(entry)
} {
    Permissions("Permissions", { entry ->
        val styleRead = TextColors.rgb(config.colors.permissionRead)
        val styleWrite = TextColors.rgb(config.colors.permissionWrite)
        val styleExecute = TextColors.rgb(config.colors.permissionExecute)

        fun render(perm: Stat.Mode.Permissions): String {
            val r = if (perm.canRead) styleRead("r") else TextStyles.dim("-")
            val w = if (perm.canWrite) styleWrite("w") else TextStyles.dim("-")
            val x = if (perm.canExecute) styleExecute("x") else TextStyles.dim("-")
            return "$r$w$x"
        }

        Text("${render(entry.stat.mode.user)}${render(entry.stat.mode.group)}${render(entry.stat.mode.others)}")
    }),

    HardLinkCount("#HL", { entry ->
        Text(TextColors.rgb(config.colors.hardlinkCount)("${entry.stat.hardlinkCount}"))
    }),

    UserName("User", { entry ->
        Text(entry.userName?.let { TextColors.rgb(config.colors.user)(it) } ?: TextStyles.dim("?"))
    }),

    GroupName("Group", { entry ->
        Text(entry.groupName?.let { TextColors.rgb(config.colors.group)(it) } ?: TextStyles.dim("?"))
    }),

    @Suppress("detekt:MagicNumber")
    EntrySize("Size", render@{ entry ->
        val bytes = entry.size ?: return@render Text("", align = TextAlign.RIGHT)

        val numStyle = TextColors.rgb(config.colors.entrySize)
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
    LastModified("Last Modified", { entry ->
        val instant = entry.stat.lastModificationTime
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

        val brightnessRange = config.modificationTime.minimumBrightness..1.0
        val brightness = factor * (brightnessRange.endInclusive - brightnessRange.start) + brightnessRange.start

        val rgb = RGB(config.colors.modificationTime)
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
