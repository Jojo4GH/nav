package de.jonasbroeckmann.nav.framework.ui

fun Appendable.appendTextFieldContent(
    text: String,
    hasFocus: Boolean,
    placeholder: String? = null
) {
    if (placeholder != null && text.isEmpty()) {
        append(placeholder)
    } else {
        append(text)
        if (hasFocus) {
            append('_')
        }
    }
}
