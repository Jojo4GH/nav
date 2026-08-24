package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.values.MacroValueStorageType

context(logger: Logger)
internal fun warnPropertyUnknown(expression: MacroExpression) {
    logger.warning("'${expression.path}' is not a known property")
}

context(logger: Logger)
internal fun warnCustomStorageNotSupported(type: MacroValueStorageType.Custom) {
    logger.warning("Custom macro storage type '${type.key}' is currently not supported")
}
