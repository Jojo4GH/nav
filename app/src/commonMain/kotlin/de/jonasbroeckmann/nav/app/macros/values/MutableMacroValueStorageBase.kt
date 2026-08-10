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
): MacroValue.Dictionary = computeUpdatedDictionary(
    data = UpdateData(path.operators, newValue),
    operator = path.key
)

private data class UpdateData(
    val operators: List<Operator>,
    val newValue: MacroValue?,
    val index: Int = 0
) {
    val operator get() = operators.getOrNull(index)

    val currentPath get() = MacroPathExpression(operators.take(index + 1))

    val next get() = copy(index = index + 1)
}

context(logger: Logger)
private fun MacroValue?.computeUpdated(data: UpdateData): MacroValue? = when (val operator = data.operator) {
    is Operator.Key -> computeUpdatedDictionary(data, operator)
    is Operator.Index -> computeUpdatedArray(data, operator)
    is Operator.Function -> computeUpdatedFunction(data, operator)
    null -> data.newValue
}

context(logger: Logger)
private fun MacroValue?.computeUpdatedDictionary(data: UpdateData, operator: Operator.Key): MacroValue.Dictionary {
    return expectValueOfTypeOrDefault(MacroValue.Dictionary, data).updated(operator.key) {
        it.computeUpdated(data.next)
    }
}

context(logger: Logger)
private fun MacroValue?.computeUpdatedArray(data: UpdateData, operator: Operator.Index): MacroValue.Array {
    return expectValueOfTypeOrDefault(MacroValue.Array, data).updated(operator.index) {
        it.computeUpdated(data.next)
    }
}

context(logger: Logger)
private fun MacroValue?.computeUpdatedFunction(data: UpdateData, operator: Operator.Function) = when (operator) {
    Last -> computeUpdatedFunctionLast(data)
    Next -> computeUpdatedFunctionNext(data)
    Keys, Values, Size -> computeUpdatedFunctionUnsupported(data, operator)
}

context(logger: Logger)
private fun MacroValue?.computeUpdatedFunctionLast(data: UpdateData): MacroValue.Array {
    val current = expectValueOfTypeOrDefault(MacroValue.Array, data)
    if (current.isEmpty()) throwHere(data, "Cannot set last element of empty array")
    return current.updated(current.lastIndex) {
        it.computeUpdated(data.next)
    }
}

context(logger: Logger)
private fun MacroValue?.computeUpdatedFunctionNext(data: UpdateData): MacroValue.Array {
    val current = expectValueOfTypeOrDefault(MacroValue.Array, data)
    return current.updated(current.size) {
        it.computeUpdated(data.next)
    }
}

private fun computeUpdatedFunctionUnsupported(
    data: UpdateData,
    operator: Operator.Function
): Nothing = throwHere(data, "Cannot use function '${operator.name}' here")

context(logger: Logger)
private fun <T : MacroValue> MacroValue?.expectValueOfTypeOrDefault(type: MacroValue.Type<T>, data: UpdateData): T {
    if (this == null) return type.default
    return type.safeCast(this) ?: run {
        logger.warningOnDebug { "At '${data.currentPath}': Expected ${type.name}, but is ${this.type.name}. Replacing with default value." }
        type.default
    }
}

private fun throwHere(
    data: UpdateData,
    message: String
): Nothing = throw MacroValueStorageException(
    "Error at '${data.currentPath}': $message"
)
