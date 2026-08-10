package de.jonasbroeckmann.nav.app.ui.dialogs

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.widgets.HorizontalRule
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.components.Macro.Companion.computeStyle
import de.jonasbroeckmann.nav.app.macros.context.MacroEvaluationScope
import de.jonasbroeckmann.nav.framework.ui.Decorator

context(_: MacroEvaluationScope, _: MacroTraceContext)
fun macroDialogDecorator(macro: Macro) = Decorator { dialog ->
    verticalLayout {
        val style = macro.computeStyle() + TextStyles.dim
        cell(
            HorizontalRule(
                title = macro.description.evaluate()
                    .takeUnless { it.isEmpty() }
                    ?.let { style(it) }
                    .orEmpty(),
                titleAlign = TextAlign.LEFT,
                ruleStyle = style
            )
        )
        cell(dialog)
    }
}
