package de.jonasbroeckmann.nav.app.macros.context

import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType
import de.jonasbroeckmann.nav.command.Logger

context(logger: Logger)
internal fun warnPropertyUnknown(expression: MacroExpression) {
    logger.terminal.warning("'${expression.path}' is not a known property")
}

context(logger: Logger)
internal fun warnCustomStorageNotSupported(type: MacroValueStorageType.Custom) {
    logger.terminal.warning("Custom macro storage type '${type.key}' is currently not supported")
}
