package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression.Operator

abstract class MutableMacroValueStorageBase : MutableMacroValueStorage {
    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        update { computeUpdated(path, newValue) }
    }

    protected abstract fun update(updater: MacroValue.Dictionary.() -> MacroValue.Dictionary)
}

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

private fun MacroValue?.computeUpdated(data: UpdateData): MacroValue? = when (val operator = data.operator) {
    is Operator.Key -> computeUpdatedDictionary(data, operator)
    is Operator.Index -> computeUpdatedArray(data, operator)
    is Operator.Function -> computeUpdatedFunction(data, operator)
    null -> data.newValue
}

private fun MacroValue?.computeUpdatedDictionary(data: UpdateData, operator: Operator.Key): MacroValue.Dictionary {
    if (this !is MacroValue.Dictionary?) throwUnexpectedType(data, "dictionary")
    return (this ?: MacroValue.Dictionary()).updated(operator.key) {
        it.computeUpdated(data.next)
    }
}

private fun MacroValue?.computeUpdatedArray(data: UpdateData, operator: Operator.Index): MacroValue.Array {
    if (this !is MacroValue.Array?) throwUnexpectedType(data, "array")
    return (this ?: MacroValue.Array()).updated(operator.index) {
        it.computeUpdated(data.next)
    }
}

private fun MacroValue?.computeUpdatedFunction(data: UpdateData, operator: Operator.Function) = when (operator) {
    Last -> computeUpdatedFunctionLast(data)
    Next -> computeUpdatedFunctionNext(data)
    Keys, Values, Size -> computeUpdatedFunctionUnsupported(data, operator)
}

private fun MacroValue?.computeUpdatedFunctionLast(data: UpdateData): MacroValue.Array {
    if (this !is MacroValue.Array?) throwUnexpectedType(data, "array")
    val current = this ?: MacroValue.Array()
    if (current.isEmpty()) throwHere(data, "Cannot set last element of empty array")
    return current.updated(current.lastIndex) {
        it.computeUpdated(data.next)
    }
}

private fun MacroValue?.computeUpdatedFunctionNext(data: UpdateData): MacroValue.Array {
    if (this !is MacroValue.Array?) throwUnexpectedType(data, "array")
    val current = this ?: MacroValue.Array()
    return current.updated(current.size) {
        it.computeUpdated(data.next)
    }
}

private fun computeUpdatedFunctionUnsupported(
    data: UpdateData,
    operator: Operator.Function
): Nothing = throwHere(data, "Cannot use function '${operator.name}' here")

private fun throwHere(
    data: UpdateData,
    message: String
): Nothing = throw MacroValueStorageException(
    "Cannot update value at '${data.currentPath}': $message"
)

private fun MacroValue.throwUnexpectedType(
    data: UpdateData,
    expectedType: String
): Nothing = throwHere(
    data = data,
    message = "Expected $expectedType, but is ${this.description}"
)
