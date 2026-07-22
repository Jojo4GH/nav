package de.jonasbroeckmann.nav.config

import com.github.ajalt.mordant.rendering.TextStyle
import de.jonasbroeckmann.nav.utils.parseColor
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.reflect.KProperty1

@Serializable
@JvmInline
value class StyleString(val raw: String) {

    context(_: StylesProvider)
    fun evaluate(): TextStyle = styles[raw] ?: raw.parseColor()

    companion object {
        val KProperty1<Styles, TextStyle>.styleString get() = StyleString(name)
    }
}