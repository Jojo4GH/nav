package de.jonasbroeckmann.nav.app.macros.components

import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression

object DefaultMacroExpressions {
    val ResultDefault = MacroExpression(PrivateLocal, "result")
    val ExitCode = MacroExpression(PrivateLocal, "exitCode")
}
