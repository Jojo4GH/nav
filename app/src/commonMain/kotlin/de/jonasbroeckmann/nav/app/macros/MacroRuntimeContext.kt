package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.StateUpdater
import de.jonasbroeckmann.nav.app.macros.MacroProperty.Companion.trySet
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.app.ui.dialogs.macroDialogDecorator
import de.jonasbroeckmann.nav.command.Logger
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.infoOnDebug
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.decorate
import de.jonasbroeckmann.nav.framework.utils.div

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
            ?: localStorage.toMap().asIterable().map { it.toPair() }
        input.forEach { (path, value) ->
            callContext.localStorage[path] = value
        }

        val returnEvent = interceptReturn {
            context(callContext) { runnable.run() }
        }

        val output = capture
            ?.map { (expression, evaluable) -> expression to context(callContext) { evaluable.evaluate() } }
            ?: callContext.localStorage.toMap().asIterable().map { (path, value) -> MacroExpression(Local, path) to value }
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

class MacroSessionContext(
    context: FullContext,
    stateProvider: StateProvider
) : FullContext by context, StateProvider by stateProvider {
    val sessionStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()
    val persistentStorage: MutableMacroValueStorage? = run init@{
        val path = (configPath ?: Config.findConfigPath(mustExist = false))
            ?.parent
            ?.let { it / "nav-storage.yaml" }
            ?: run {
                terminal.warning(
                    """
                    Could not find path for persistent macro storage.
                    Persistent macro storage will be unavailable.
                    """.trimIndent()
                )
                return@init null
            }
        YamlFileMacroValueStorage(path)
    }
    val environmentStorage: MutableMacroValueStorage = EnvironmentMacroValueStorage(logger = context)

    companion object {
        context(context: FullContext, stateProvider: StateProvider)
        operator fun invoke() = MacroSessionContext(context, stateProvider)
    }
}

interface MacroEvaluationScope : FullContext, StateProvider {
    operator fun get(expression: MacroExpression): MacroValue?

    companion object {
        context(_: FullContext, _: StateProvider)
        val Empty: MacroEvaluationScope get() = MacroEvaluationScopeBase(MacroSessionContext())
    }
}

interface MacroStorageScope : MacroEvaluationScope, StateUpdater {
    operator fun set(expression: MacroExpression, value: MacroValue?)

    companion object {
        context(_: MacroTraceContext)
        operator fun MacroStorageScope.set(expression: ExpressionString, value: String?) {
            this[expression.evaluate()] = MacroValue(value)
        }
    }
}

open class MacroEvaluationScopeBase(
    protected val sessionContext: MacroSessionContext,
) : MacroEvaluationScope, FullContext by sessionContext, StateProvider by sessionContext {
    protected open val localStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()

    override operator fun get(expression: MacroExpression): MacroValue? {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            return property.get()
        }
        return when (val type = expression.storageType) {
            Property -> {
                warnPropertyUnknown(expression)
                null
            }
            null, Local -> localStorage[expression.path]
            Session -> sessionContext.sessionStorage[expression.path]
            Persistent -> sessionContext.persistentStorage?.get(expression.path)
            Environment -> sessionContext.environmentStorage[expression.path]
            is MacroValueStorageType.Custom -> {
                warnCustomStorageNotSupported(type)
                null
            }
        }
    }
}

open class MacroStorageScopeBase(
    stateUpdater: StateUpdater,
    sessionContext: MacroSessionContext,
) : MacroEvaluationScopeBase(sessionContext), MacroStorageScope, StateUpdater by stateUpdater {
    override operator fun set(expression: MacroExpression, value: MacroValue?) {
        val property = KnownMacroProperty.from(expression)
        if (property != null) {
            property.trySet(value, printOnFail = true)
            return
        }
        when (val type = expression.storageType) {
            Property -> warnPropertyUnknown(expression)
            null, Local -> localStorage[expression.path] = value
            Session -> sessionContext.sessionStorage[expression.path] = value
            Persistent -> sessionContext.persistentStorage?.set(expression.path, value)
            Environment -> sessionContext.environmentStorage[expression.path] = value
            is MacroValueStorageType.Custom -> warnCustomStorageNotSupported(type)
        }
    }
}

context(logger: Logger)
private fun warnPropertyUnknown(expression: MacroExpression) {
    logger.terminal.warning("'${expression.path}' is not a known property")
}

context(logger: Logger)
private fun warnCustomStorageNotSupported(type: MacroValueStorageType.Custom) {
    logger.terminal.warning("Custom macro storage type '${type.key}' is currently not supported")
}
