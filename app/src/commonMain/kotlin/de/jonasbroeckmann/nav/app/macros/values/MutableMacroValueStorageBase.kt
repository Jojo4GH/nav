package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Operator
import de.jonasbroeckmann.nav.warningOnDebug

abstract class MutableMacroValueStorageBase(protected val logger: Logger) : MutableMacroValueStorage {
    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        update { computeUpdated(path, newValue, logger = logger) }
    }

    protected abstract fun update(updater: MacroValue.Dictionary.() -> MacroValue.Dictionary)
}

context(logger: Logger)
private fun MacroValue.Dictionary.computeUpdated(
    path: MacroPathExpression,
    newValue: MacroValue?
): MacroValue.Dictionary = computeUpdatedFor(
    data = UpdateData(path.operators, newValue, logger = logger),
    operator = path.key
)

private data class UpdateData(
    val operators: List<Operator>,
    val newValue: MacroValue?,
    val index: Int = 0,
    val logger: Logger
) : Operator.Scope, Logger by logger {
    val operator get() = operators.getOrNull(index)

    val currentPath get() = MacroPathExpression(operators.take(index + 1))

    val next get() = copy(index = index + 1)

    override fun <T : MacroValue> MacroValue?.expectValueOfTypeOrDefault(type: MacroValue.Type<T>): T {
        if (this == null) return type.default
        return type.safeCast(this) ?: run {
            warningOnDebug {
                "At '$currentPath': Expected ${type.name}, but is ${this.type.name}. Replacing with default value."
            }
            type.default
        }
    }

    override fun throwHere(message: String): Nothing = throw MacroValueStorageException(
        "Error at '$currentPath': $message"
    )
}

context(logger: Logger)
private fun MacroValue?.computeUpdated(data: UpdateData): MacroValue? = when (val operator = data.operator) {
    is Operator.Writable<*> -> computeUpdatedFor(data, operator)
    is Operator.Function -> data.throwHere("Cannot use function '${operator.name}' here")
    null -> data.newValue
}

context(logger: Logger)
private fun <T : MacroValue> MacroValue?.computeUpdatedFor(data: UpdateData, operator: Operator.Writable<T>): T {
    return with(operator) { data.update(this@computeUpdatedFor) { it.computeUpdated(data.next) } }
}
