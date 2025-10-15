package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.widgets.HorizontalRule
import de.jonasbroeckmann.nav.app.macros.Macro
import de.jonasbroeckmann.nav.app.macros.MacroRuntimeContext
import de.jonasbroeckmann.nav.app.ui.style
import de.jonasbroeckmann.nav.framework.ui.Decorator

context(_: MacroRuntimeContext)
fun macroDialogDecorator(macro: Macro) = Decorator { dialog ->
    verticalLayout {
        val style = macro.style + TextStyles.dim
        cell(
            HorizontalRule(
                title = style(macro.description.evaluate()),
                titleAlign = TextAlign.LEFT,
                ruleStyle = style
            )
        )
        cell(dialog)
    }
}
