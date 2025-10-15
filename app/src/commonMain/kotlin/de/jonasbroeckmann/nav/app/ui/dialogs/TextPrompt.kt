package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.actions.buildKeyActions
import de.jonasbroeckmann.nav.app.actions.handle
import de.jonasbroeckmann.nav.app.actions.register
import de.jonasbroeckmann.nav.app.state.semantics.updateTextField
import de.jonasbroeckmann.nav.app.ui.buildHints
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.config
import de.jonasbroeckmann.nav.config.styles

context(_: StylesProvider, _: ConfigProvider)
fun DialogShowScope.defaultTextPrompt(
    title: String,
    initialText: String = "",
    placeholder: String? = null,
    validate: (String) -> Boolean = { true },
): String? = textPrompt(
    title = title,
    initialText = initialText,
    placeholder = placeholder,
    validate = validate,
    showHints = !config.hideHints,
    submitKey = config.keys.submit,
    clearKey = config.keys.filter.clear,
    cancelKey = config.keys.cancel
)

context(stylesProvider: StylesProvider)
fun DialogShowScope.textPrompt(
    title: String,
    initialText: String = "",
    placeholder: String? = null,
    showHints: Boolean,
    submitKey: KeyboardEvent,
    clearKey: KeyboardEvent? = null,
    cancelKey: KeyboardEvent? = null,
    validate: (String) -> Boolean = { true }
): String? {
    val actions: List<DialogKeyAction<TextPromptState, String?>> = buildKeyActions {
        if (clearKey != null) {
            register(
                clearKey,
                description = { "clear" },
                condition = { text.isNotEmpty() },
                action = { updateState { copy(text = "") } },
            )
        }
        if (cancelKey != null) {
            register(
                cancelKey,
                description = { "cancel" },
                condition = { true },
                action = { dismissDialog(null) },
            )
        }
        register(
            submitKey,
            description = { "submit" },
            condition = { validate(text) },
            action = { dismissDialog(text) },
        )
    }
    return inputDialog(
        initialState = TextPromptState(
            text = initialText
        ),
        onInput = onInput@{ input ->
            if (actions.handle(state, input)) return@onInput
            input.updateTextField(state.text) { newText ->
                updateState { copy(text = newText) }
            }
        }
    ) {
        verticalLayout {
            align = LEFT
            cell(title)
            cell(
                buildString {
                    append("❯ ")
                    if (text.isEmpty()) {
                        append(TextStyles.dim(placeholder ?: "type input"))
                    } else {
                        append(text)
                        append("_")
                    }
                }
            )
            if (showHints) {
                cell(
                    buildHints {
                        addActions(actions, this@inputDialog)
                        if (!validate(text)) {
                            add { styles.genericElements("input not valid") }
                        }
                    }
                )
            }
        }
    }
}

private data class TextPromptState(
    val text: String
)
