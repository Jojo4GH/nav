package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextAlign.LEFT
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import de.jonasbroeckmann.nav.app.ui.render
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.config.StylesProvider
import de.jonasbroeckmann.nav.config.config
import de.jonasbroeckmann.nav.config.styles
import de.jonasbroeckmann.nav.framework.action.DialogKeyAction
import de.jonasbroeckmann.nav.framework.action.KeyAction
import de.jonasbroeckmann.nav.framework.action.buildDialogKeyActions
import de.jonasbroeckmann.nav.framework.action.buildKeyActions
import de.jonasbroeckmann.nav.framework.action.handle
import de.jonasbroeckmann.nav.framework.action.register
import de.jonasbroeckmann.nav.framework.semantics.updateTextField
import de.jonasbroeckmann.nav.framework.ui.appendTextFieldContent
import de.jonasbroeckmann.nav.framework.ui.buildHints
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogController
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.dismissDialog
import de.jonasbroeckmann.nav.framework.ui.dialog.updateState

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
    val inputTextAction: DialogKeyAction<TextPromptState, String?>
    val actions = buildDialogKeyActions<TextPromptState, String?> {
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
        inputTextAction = register(
            KeyAction(
                keys = null,
                condition = { true },
                action = { input ->
                    input.updateTextField(
                        current = text,
                        onChange = { newText -> updateState { copy(text = newText) } }
                    )
                }
            )
        )
    }
    return inputDialog(
        initialState = TextPromptState(
            text = initialText
        ),
        onInput = { input -> actions.handle(state, input, inputMode) }
    ) {
        verticalLayout {
            align = LEFT
            cell(title)
            cell(
                buildString {
                    append("❯ ")
                    appendTextFieldContent(
                        text = text,
                        placeholder = placeholder ?: "type input",
                        hasFocus = inputTextAction.isAvailable(inputMode)
                    )
                }
            )
            if (showHints) {
                cell(
                    buildHints(styles.genericElements(" • ")) {
                        addActions(actions, this@inputDialog, inputMode) { render() }
                        if (!validate(text)) {
                            add { (TextStyles.dim + styles.danger)("input not valid") }
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
