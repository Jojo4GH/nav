package de.jonasbroeckmann.nav.app.macros

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializerOrNull

data class MacroTraceContext(
    private val parent: MacroTraceContext? = null,
    private val traceElement: () -> MacroTraceElement?
) {
    val currentTrace: List<MacroTraceElement> by lazy {
        val parentTrace = parent?.currentTrace ?: emptyList()
        val traceElement = traceElement() ?: return@lazy parentTrace
        listOf(traceElement) + parentTrace
    }

    fun traceToString(indent: String = "  ") = currentTrace.joinToString("\n") { "${indent}at $it" }

    companion object {
        val Empty = MacroTraceContext(traceElement = { null })
    }
}

context(traceContext: MacroTraceContext)
inline fun <R> macroTrace(noinline traceElement: () -> MacroTraceElement?, block: context(MacroTraceContext) () -> R): R {
    val newContext = MacroTraceContext(traceContext, traceElement)
    try {
        return context(newContext, block)
    } catch (e: Exception) {
        throw MacroException(newContext, e)
    }
}

context(_: MacroTraceContext)
inline fun <R> macroTrace(runnable: MacroRunnable, block: context(MacroTraceContext) () -> R): R = macroTrace(
    traceElement = {
        when (runnable) {
            is Macro -> MacroTraceElement.Call(runnable)
            is MacroAction.RunMacro.Delegate -> MacroTraceElement.Call(runnable.macro)
            is MacroAction -> null
            is MacroActions -> null
        }
    },
    block = block
)

context(_: MacroTraceContext)
inline fun <R> MacroCondition.macroTrace(block: context(MacroTraceContext) () -> R): R = macroTrace(
    traceElement = { MacroTraceElement.Condition(this) },
    block = block
)

context(_: MacroTraceContext)
inline fun <R> MacroAction.macroTrace(block: context(MacroTraceContext) () -> R): R = macroTrace(
    traceElement = { MacroTraceElement.Action(this) },
    block = block
)

sealed interface MacroTraceElement {
    data class Call(val macro: Macro) : MacroTraceElement {
        override fun toString(): String {
            val id = if (macro.id != null) "'${macro.id}'" else "without id"
            return "call to macro $id"
        }
    }

    data class Condition(val condition: MacroCondition) : MacroTraceElement {
        @OptIn(InternalSerializationApi::class)
        override fun toString(): String {
            val serialName = condition::class.serializerOrNull()?.descriptor?.serialName ?: condition::class.simpleName
            return "condition $serialName"
        }
    }

    data class ConditionAtIndex(val index: Int, val condition: MacroCondition) : MacroTraceElement {
        override fun toString(): String = "condition #${index + 1}"
    }

    data class Action(val action: MacroAction) : MacroTraceElement {
        @OptIn(InternalSerializationApi::class)
        override fun toString(): String {
            val serialName = action::class.serializerOrNull()?.descriptor?.serialName ?: action::class.simpleName
            return "action $serialName"
        }
    }

    data class ActionAtIndex(val index: Int, val action: MacroAction) : MacroTraceElement {
        override fun toString(): String = "action #${index + 1}"
    }
}
