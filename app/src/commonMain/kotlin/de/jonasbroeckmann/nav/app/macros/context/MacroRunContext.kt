package de.jonasbroeckmann.nav.app.macros.context

import de.jonasbroeckmann.nav.Logger
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.macros.MacroEvaluable
import de.jonasbroeckmann.nav.app.macros.MacroException
import de.jonasbroeckmann.nav.app.macros.MacroTraceContext
import de.jonasbroeckmann.nav.app.macros.components.MacroCallable
import de.jonasbroeckmann.nav.app.macros.components.Macro
import de.jonasbroeckmann.nav.app.macros.expressions.MacroExpression
import de.jonasbroeckmann.nav.app.macros.expressions.MacroPathExpression
import de.jonasbroeckmann.nav.app.macros.macroTrace
import de.jonasbroeckmann.nav.app.macros.values.InMemoryMacroValueStorage
import de.jonasbroeckmann.nav.app.macros.values.MacroValue
import de.jonasbroeckmann.nav.app.ui.dialogs.macroDialogDecorator
import de.jonasbroeckmann.nav.dangerThrowable
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.decorate
import de.jonasbroeckmann.nav.infoOnDebug

interface MacroRunContext {
    val controller: MainController
    val rootMacro: Macro

    companion object {
        context(controller: MainController)
        fun run(callable: MacroCallable) = MacroRunContextImpl(controller, callable).run()
    }
}

private class MacroRunContextImpl(
    override val controller: MainController,
    private val callable: MacroCallable
) : MacroRunContext, Logger by controller {
    override val rootMacro get() = callable.macro

    val runStorage = InMemoryMacroValueStorage()

    fun run() = MacroException.handle(
        onException = { e ->
            if (controller.debugMode) {
                controller.dangerThrowable(e, "Error while running macro")
            }
            controller.danger(e)
        }
    ) {
        macroTrace(rootMacro) {
            val callContext = MacroCallScopeImpl(
                rootContext = this,
                currentMacro = rootMacro,
                returnAction = {
                    returnToRoot()
                }
            )

            interceptReturnEvent {
                context(callContext) { callable.run() }
            }
        }
    }
}

private class MacroCallScopeImpl private constructor(
    override val rootContext: MacroRunContextImpl,
    override val parentCall: MacroCallScope?,
    override val currentMacro: Macro,
    private val returnAction: () -> Nothing
) : MacroCallScope,
    MacroRunContext by rootContext,
    MacroStorageScope by MacroStorageScopeBase(
        fullContext = rootContext.controller,
        stateProvider = rootContext.controller,
        stateUpdater = rootContext.controller,
        sessionContext = rootContext.controller,
        macro = currentMacro,
        sharedLocalStorage = rootContext.runStorage
    ),
    MacroReportContext by MacroReportContextImpl(logger = rootContext.controller)
{
    constructor(
        rootContext: MacroRunContextImpl,
        currentMacro: Macro,
        returnAction: () -> Nothing
    ) : this(rootContext, null, currentMacro, returnAction)

    constructor(
        parentCall: MacroCallScopeImpl,
        currentMacro: Macro,
        returnAction: () -> Nothing
    ) : this(parentCall.rootContext, parentCall, currentMacro, returnAction)

    private val localStorage = InMemoryMacroValueStorage()

    context(_: MacroTraceContext)
    override fun call(
        parameters: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>?,
        capture: Iterable<Pair<MacroExpression, MacroEvaluable<MacroValue?>>>?,
        returnToRoot: Boolean,
        callable: MacroCallable
    ): Unit = macroTrace(callable) {
        val subCallContext = MacroCallScopeImpl(
            parentCall = this,
            currentMacro = callable.macro,
            returnAction = {
                if (returnToRoot) {
                    returnToRoot()
                } else {
                    returnTo(this)
                }
            }
        )

        val input = parameters
            ?.map { (expression, evaluable) ->
                require(expression.storageType is PrivateLocal?) { "'${expression}' is not in local storage" }
                expression.path to context(this@MacroCallScopeImpl) { evaluable.evaluate() }
            }
            ?: localStorage.value().map { (key, value) -> MacroPathExpression(key) to value }
        input.forEach { (path, value) ->
            subCallContext.localStorage[path] = value
        }

        val returnEvent = interceptReturnEvent {
            context(subCallContext) { callable.run() }
        }

        val output = capture
            ?.map { (expression, evaluable) -> expression to context(subCallContext) { evaluable.evaluate() } }
            ?: subCallContext.localStorage.value().map { (path, value) -> MacroExpression(PrivateLocal, path) to value }
        output.forEach { (expression, value) ->
            this[expression] = value
        }

        returnEvent?.handle()
    }

    override fun doReturn(): Nothing = returnAction()

    context(_: MacroTraceContext)
    override fun <R> showMacroDialog(
        options: DialogOptions,
        block: DialogShowScope.() -> R
    ) = rootContext.controller.showDialog(options) {
        decorate(macroDialogDecorator(rootContext.rootMacro), block)
    }
}

private class MacroReportContextImpl(private val logger: Logger) : MacroReportContext {
    context(traceContext: MacroTraceContext)
    private fun buildReportMessage(message: String, includeTrace: Boolean = true) = buildString {
        append(message)
        if (includeTrace) {
            appendLine()
            append(traceContext.traceToString())
        }
    }

    context(traceContext: MacroTraceContext)
    override fun reportDebug(includeTrace: Boolean, message: () -> String) {
        logger.infoOnDebug { buildReportMessage(message(), includeTrace) }
    }

    context(traceContext: MacroTraceContext)
    override fun reportWarning(includeTrace: Boolean, message: () -> String) {
        logger.warning(buildReportMessage(message(), includeTrace))
    }
}

private fun returnTo(target: MacroCallScope): Nothing = throw MacroReturnEvent(target)

private fun returnToRoot(): Nothing = throw MacroReturnEvent(null)

@IgnorableReturnValue
private inline fun interceptReturnEvent(block: () -> Unit): MacroReturnEvent? {
    try {
        block()
        return null
    } catch (returnEvent: MacroReturnEvent) {
        return returnEvent
    }
}

private class MacroReturnEvent(private val target: MacroCallScope?) : Throwable() {
    context(scope: MacroCallScope)
    fun handle() {
        if (scope !== target) {
            throw this
        }
    }
}
