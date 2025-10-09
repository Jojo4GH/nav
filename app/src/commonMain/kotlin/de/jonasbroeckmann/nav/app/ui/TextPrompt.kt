package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.table.table
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.updateTextField


private data class TextPromptState(
    val input: String
)


fun FullContext.showTextPrompt(
    title: String,
    default: String? = null,
    placeholder: String? = null,
    cancelable: Boolean = false,
    validate: (String) -> Boolean = { true }
): String? {
    showSimpleInputAnimation(
        initialState = TextPromptState(
            input = default ?: ""
        ),
        onInput = { event ->
            if (cancelable && event == config.keys.cancel) {
                return@showTextPrompt null
            }
            if (event == config.keys.submit && validate(state.input)) {
                return@showTextPrompt state.input
            }
            event.updateTextField(state.input) { newInput ->
                state = state.copy(input = newInput)
            }
        }
    ) {
        table {
            header {  }
        }
    }
    TODO()
}
