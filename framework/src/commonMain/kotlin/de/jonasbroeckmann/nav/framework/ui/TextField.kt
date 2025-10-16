package de.jonasbroeckmann.nav.framework.ui

import com.github.ajalt.mordant.rendering.TextStyles

fun Appendable.appendTextFieldContent(
    text: String,
    hasFocus: Boolean,
    placeholder: String? = null
) {
    if (placeholder != null && text.isEmpty()) {
        append(TextStyles.dim(placeholder))
    } else {
        append(text)
        if (hasFocus) {
            append('_')
        }
    }
}

fun buildTextFieldContent(
    text: String,
    hasFocus: Boolean,
    placeholder: String? = null
): String = buildString {
    appendTextFieldContent(
        text = text,
        hasFocus = hasFocus,
        placeholder = placeholder
    )
}
