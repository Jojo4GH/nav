package de.jonasbroeckmann.nav.app.ui

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.actions.buildKeyActions
import de.jonasbroeckmann.nav.app.actions.register
import de.jonasbroeckmann.nav.app.updateTextField
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.styles
import kotlin.time.Duration


private data class TextPromptState(
    val text: String
)

context(context: PartialContext, stylesProvider: StylesProvider)
fun DialogRenderingScope.textPrompt(
    title: String,
    initialText: String = "",
    placeholder: String? = null,
    showHints: Boolean,
    submitKey: KeyboardEvent,
    clearKey: KeyboardEvent? = null,
    cancelKey: KeyboardEvent? = null,
    validate: (String) -> Boolean = { true },
    inputTimeout: Duration
): String? {
    val actions = buildKeyActions<DialogScope<TextPromptState, String?>, Unit> {
        if (clearKey != null) {
            register(
                clearKey,
                description = { "clear" },
                condition = { state.text.isNotEmpty() },
                action = { state = state.copy(text = "") },
            )
        }
        if (cancelKey != null) {
            register(
                cancelKey,
                description = { "cancel" },
                condition = { true },
                action = { closeWith(null) },
            )
        }
        register(
            submitKey,
            description = { "submit" },
            condition = { validate(state.text) },
            action = { closeWith(state.text) },
        )
    }
    return dialog(
        initialState = TextPromptState(
            text = initialText
        ),
        actions = actions,
        onUnhandledInput = { input ->
            input.updateTextField(state.text) { newText ->
                state = state.copy(text = newText)
            }
        },
        inputTimeout = inputTimeout,
    ) { state ->
        verticalLayout {
            align = LEFT
            cell(title)
            cell(
                buildString {
                    append("❯ ")
                    if (state.text.isEmpty()) {
                        append(TextStyles.dim(placeholder ?: "type input"))
                    } else {
                        append(state.text)
                        append("_")
                    }
                }
            )
            if (showHints) {
                cell(
                    buildHints<DialogScope<TextPromptState, String?>> {
                        addActions(actions, this@dialog)
                        if (!validate(state.text)) {
                            add { styles.genericElements("input not valid") }
                        }
                    }
                )
            }
        }
    }
}
