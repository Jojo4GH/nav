package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.command.Logger
import de.jonasbroeckmann.nav.command.warningOnDebug
import de.jonasbroeckmann.nav.utils.getEnvironmentVariable
import de.jonasbroeckmann.nav.utils.setEnvironmentVariable

class EnvironmentMacroValueStorage(
    private val logger: Logger
) : MutableMacroValueStorage {
    override fun get(path: MacroPathExpression): MacroValue.Text? = path.environmentVariable()
        ?.let { getEnvironmentVariable(it) }
        ?.let { MacroValue.Text(it) }

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        path.environmentVariable()?.let { variable ->
            if (newValue !is MacroValue.Text?) {
                logger.warningOnDebug { "Invalid non-text value for environment variable: ${newValue.stringify()}" }
                return
            }
            setEnvironmentVariable(variable, newValue?.value)
        }
    }

    private fun MacroPathExpression.environmentVariable(): String? {
        val operator = operators.singleOrNull()
        if (operator !is MacroPathExpression.Operator.Key) {
            logger.warningOnDebug { "Invalid path expression for environment variable: $this" }
            return null
        }
        return operator.key
    }
}
