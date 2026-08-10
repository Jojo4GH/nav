package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.utils.EnvironmentVariables
import de.jonasbroeckmann.nav.warningOnDebug

class EnvironmentMacroValueStorage(
    private val logger: Logger
) : MutableMacroValueStorage {
    override fun get(path: MacroPathExpression): MacroValue.Text? = path.environmentVariable()
        ?.let { EnvironmentVariables[it] }
        ?.let { MacroValue.Text(it) }

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        path.environmentVariable()?.let { variable ->
            if (newValue !is MacroValue.Text?) {
                logger.warningOnDebug { "Invalid non-text value for environment variable: ${newValue.stringify()}" }
                return
            }
            EnvironmentVariables[variable] = newValue?.value
        }
    }

    override fun value() = MacroValue.Dictionary(EnvironmentVariables.get().mapValues { (_, value) -> MacroValue.Text(value) })

    private fun MacroPathExpression.environmentVariable(): String? {
        val operator = operators.singleOrNull()
        if (operator !is MacroPathExpression.Operator.Key) {
            logger.warningOnDebug { "Invalid path expression for environment variable: $this" }
            return null
        }
        return operator.key
    }
}
