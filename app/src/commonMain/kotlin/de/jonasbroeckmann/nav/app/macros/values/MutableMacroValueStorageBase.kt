package de.jonasbroeckmann.nav.app.macros.values

import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression

abstract class MutableMacroValueStorageBase : MutableMacroValueStorage {
    override fun get(path: MacroPathExpression) = get().evaluate(path)

    override fun set(path: MacroPathExpression, newValue: MacroValue?) {
        update { computeUpdated(path, newValue) }
    }

    protected abstract fun get(): MacroValue

    protected abstract fun update(updater: MacroValue.() -> MacroValue)
}

private fun MacroValue.evaluate(path: MacroPathExpression) = path.operators.fold<_, MacroValue?>(this) { value, operator ->
    when (operator) {
        is MacroPathExpression.Operator.Key if value is MacroValue.Dictionary -> value[operator.key]
        is MacroPathExpression.Operator.Index if value is MacroValue.Array -> value.getOrNull(operator.index)
        is MacroPathExpression.Operator.Function -> when (operator) {
            Last if value is MacroValue.Array -> value.lastOrNull()
            Next if value is MacroValue.Array -> null
            Keys if value is MacroValue.Dictionary -> MacroValue.Array(value.keys.map { MacroValue.Text(it) })
            Values if value is MacroValue.Dictionary -> MacroValue.Array(value.values.toList())
            Size if value is MacroValue.Collection -> MacroValue.Text("${value.size}")
            else -> null
        }
        else -> null
    }
}

private fun MacroValue.computeUpdated(
    path: MacroPathExpression,
    newValue: MacroValue?
): MacroValue {
    return computeUpdated(
        path = emptyList(),
        restPath = path.operators,
        newValue = newValue
    ) ?: MacroValue.Dictionary()
}

private data class MacroValueUpdateContext(
    val currentPath: MacroPathExpression,
    val operator: MacroPathExpression.Operator?,
    val restPath: List<MacroPathExpression.Operator>,
    val newValue: MacroValue?
)

private fun MacroValue?.computeUpdated(
    path: List<MacroPathExpression.Operator>,
    restPath: List<MacroPathExpression.Operator>,
    newValue: MacroValue?
): MacroValue? = enterContext(path, restPath, newValue) { operator ->
    when (operator) {
        is MacroPathExpression.Operator.Key -> {
            if (this !is MacroValue.Dictionary?) throwUnexpectedType("dictionary")
            (this ?: MacroValue.Dictionary()).updated(operator.key) {
                it.computeUpdated()
            }
        }
        is MacroPathExpression.Operator.Index -> {
            if (this !is MacroValue.Array?) throwUnexpectedType("array")
            (this ?: MacroValue.Array()).updated(operator.index) {
                it.computeUpdated()
            }
        }
        is MacroPathExpression.Operator.Function -> when (operator) {
            Last -> {
                if (this !is MacroValue.Array?) throwUnexpectedType("array")
                val current = this ?: MacroValue.Array()
                if (current.isEmpty()) throwHere("Cannot set last element of empty array")
                current.updated(current.lastIndex) {
                    it.computeUpdated()
                }
            }
            Next -> {
                if (this !is MacroValue.Array?) throwUnexpectedType("array")
                val current = this ?: MacroValue.Array()
                current.updated(current.size) {
                    it.computeUpdated()
                }
            }
            Keys -> throwHere("Cannot use function '${MacroPathExpression.Operator.Function.Keys.name}' here")
            Values -> throwHere("Cannot use function '${MacroPathExpression.Operator.Function.Values.name}' here")
            Size -> throwHere("Cannot use function '${MacroPathExpression.Operator.Function.Size.name}' here")
        }
        null -> newValue
    }
}

private inline fun enterContext(
    path: List<MacroPathExpression.Operator>,
    restPath: List<MacroPathExpression.Operator>,
    newValue: MacroValue?,
    block: context(MacroValueUpdateContext) (MacroPathExpression.Operator?) -> MacroValue?
): MacroValue? = context(
    MacroValueUpdateContext(
        currentPath = MacroPathExpression(restPath.firstOrNull()?.let { path + it } ?: path),
        operator = restPath.firstOrNull(),
        restPath = restPath.drop(1),
        newValue = newValue
    )
) {
    block(contextOf<MacroValueUpdateContext>().operator)
}

context(context: MacroValueUpdateContext)
private fun MacroValue?.computeUpdated() = computeUpdated(
    path = context.currentPath.operators,
    restPath = context.restPath,
    newValue = context.newValue
)

context(context: MacroValueUpdateContext)
private fun throwHere(message: String): Nothing = throw MacroValueStorageException(
    "Cannot update value at '${context.currentPath}': $message"
)

context(context: MacroValueUpdateContext)
private fun MacroValue.throwUnexpectedType(expectedType: String): Nothing = throwHere(
    "Expected $expectedType, but is ${this.description}"
)
