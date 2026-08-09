package de.jonasbroeckmann.nav.app.macros.context

import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroException
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.components.MacroRunnable
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.macroTrace
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.ui.dialogs.macroDialogDecorator
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.infoOnDebug
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.decorate

class MacroRuntimeContext private constructor(
    val controller: MainController,
    sessionContext: MacroSessionContext,
    private val rootMacro: Macro
) : MacroStorageScopeBase(controller, sessionContext) {
    override val localStorage: InMemoryMacroValueStorage = InMemoryMacroValueStorage()

    fun <R> showMacroDialog(
        options: DialogOptions = DialogOptions(),
        block: DialogShowScope.() -> R
    ) = controller.showDialog(options) {
        decorate(context(MacroTraceContext.Empty) { macroDialogDecorator(rootMacro) }, block)
    }

    context(_: MacroTraceContext)
    fun call(
        parameters: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>? = emptyList(),
        capture: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>? = emptyList(),
        returnBarrier: Boolean = true,
        runnable: MacroRunnable
    ): Unit = macroTrace(runnable) {
        val callContext = MacroRuntimeContext(
            controller = controller,
            sessionContext = sessionContext,
            rootMacro = rootMacro
        )

        val input = parameters
            ?.map { (expression, evaluable) ->
                require(expression.storageType is Local?) { "'${expression}' is not in local storage" }
                expression.path to context(this@MacroRuntimeContext) { evaluable.evaluate() }
            }
            ?: localStorage.value().map { (key, value) -> MacroPathExpression(key) to value }
        input.forEach { (path, value) ->
            callContext.localStorage[path] = value
        }

        val returnEvent = interceptReturn {
            context(callContext) { runnable.run() }
        }

        val output = capture
            ?.map { (expression, evaluable) -> expression to context(callContext) { evaluable.evaluate() } }
            ?: callContext.localStorage.value().map { (path, value) -> MacroExpression(Local, path) to value }
        output.forEach { (expression, value) ->
            this[expression] = value
        }

        if (!returnBarrier && returnEvent != null) {
            throw returnEvent
        }
    }

    private inline fun interceptReturn(block: () -> Unit) = try {
        block()
        null
    } catch (ret: MacroReturnEvent) {
        ret
    }

    fun doReturn(): Nothing = throw MacroReturnEvent()

    private class MacroReturnEvent : Throwable()

    context(traceContext: MacroTraceContext)
    private fun buildReportMessage(message: String, includeTrace: Boolean = true) = buildString {
        append(message)
        if (includeTrace) {
            appendLine()
            append(traceContext.traceToString())
        }
    }

    context(traceContext: MacroTraceContext)
    fun reportDebug(includeTrace: Boolean = true, message: () -> String) {
        infoOnDebug { buildReportMessage(message(), includeTrace) }
    }

    context(traceContext: MacroTraceContext)
    fun reportWarning(message: String, includeTrace: Boolean = true) {
        terminal.warning(buildReportMessage(message, includeTrace))
    }

    companion object {
        context(controller: MainController, sessionContext: MacroSessionContext)
        fun run(macro: Macro) {
            MacroException.handle(
                onException = { e ->
                    if (controller.debugMode) {
                        controller.dangerThrowable(e, "Error while running macro")
                    }
                    controller.terminal.danger(e)
                }
            ) {
                MacroRuntimeContext(
                    controller = controller,
                    sessionContext = sessionContext,
                    rootMacro = macro
                ).call(
                    parameters = emptyList(),
                    runnable = macro
                )
            }
        }
    }
}