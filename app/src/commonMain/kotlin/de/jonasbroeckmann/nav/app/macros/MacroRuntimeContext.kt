package de.jonasbroeckmann.nav.app.macros

import com.github.ajalt.mordant.terminal.danger
import com.github.ajalt.mordant.terminal.warning
import de.jonasbroeckmann.nav.app.FullContext
import de.jonasbroeckmann.nav.app.MainController
import de.jonasbroeckmann.nav.app.state.StateProvider
import de.jonasbroeckmann.nav.app.ui.dialogs.macroDialogDecorator
import de.jonasbroeckmann.nav.command.Logger
import de.jonasbroeckmann.nav.command.PartialContext
import de.jonasbroeckmann.nav.command.dangerThrowable
import de.jonasbroeckmann.nav.command.infoOnDebug
import de.jonasbroeckmann.nav.config.Config
import de.jonasbroeckmann.nav.config.ConfigProvider
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogOptions
import de.jonasbroeckmann.nav.framework.ui.dialog.DialogShowScope
import de.jonasbroeckmann.nav.framework.ui.dialog.decorate
import de.jonasbroeckmann.nav.framework.utils.div

class MacroRuntimeContext private constructor(
    controller: MainController,
    private val sessionContext: MacroSessionContext,
    private val rootMacro: Macro
) : MacroStorageScopeBase(sessionContext), MainController by controller {
    fun <R> showMacroDialog(
        options: DialogOptions = DialogOptions(),
        block: DialogShowScope.() -> R
    ) = showDialog(options) {
        decorate(context(MacroTraceContext.Empty) { macroDialogDecorator(rootMacro) }, block)
    }

    context(_: MacroTraceContext)
    fun call(
        parameters: Map<MacroSymbol, MacroEvaluable<String>>? = emptyMap(),
        capture: Map<MacroSymbol, MacroEvaluable<String>>? = emptyMap(),
        returnBarrier: Boolean = true,
        runnable: MacroRunnable
    ): Unit = macroTrace(runnable) {
        val callContext = MacroRuntimeContext(this, sessionContext, rootMacro)

        val input = parameters
            ?.mapValues { (_, evaluable) -> context(this) { evaluable.evaluate() } }
            ?: this.localStorage
        input.forEach { (symbol, value) ->
            callContext[symbol] = value
        }

        val returnEvent = interceptReturn {
            context(callContext) { runnable.run() }
        }

        val output = capture
            ?.mapValues { (_, evaluable) -> context(callContext) { evaluable.evaluate() } }
            ?: callContext.localVariables
        output.forEach { (symbol, value) ->
            this[symbol] = value
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
        context(controller: MainController)
        fun run(macro: Macro) {
            MacroException.handle(
                onException = { e ->
                    if (controller.debugMode) {
                        controller.dangerThrowable(e, "Error while running macro")
                    }
                    controller.terminal.danger(e)
                }
            ) {
                MacroRuntimeContext(controller, rootMacro = macro).call(
                    parameters = emptyMap(),
                    runnable = macro
                )
            }
        }

        operator fun MacroRuntimeContext.set(symbolName: String, value: String) = set(MacroSymbol(symbolName), value)
    }
}

class MacroSessionContext(
    context: FullContext,
    stateProvider: StateProvider
) : FullContext by context, StateProvider by stateProvider {
    val propertyStorage: MutableMacroValueStorage = PropertyStorage(
        context,
        stateProvider,
        properties = DefaultMacroProperty.All.map { it.property }
    )
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
}

interface MacroEvaluationScope : FullContext, StateProvider {
    operator fun get(expression: MacroExpression): MacroValue?
}

interface MacroStorageScope : MacroEvaluationScope {
    operator fun set(expression: MacroExpression, value: MacroValue?)
}

open class MacroStorageScopeBase(
    private val sessionContext: MacroSessionContext
) : MacroStorageScope, FullContext by sessionContext, StateProvider by sessionContext {
    protected val localStorage: MutableMacroValueStorage = InMemoryMacroValueStorage()

    override operator fun get(expression: MacroExpression): MacroValue? {
        val t = when (val type = expression.storageType) {
            null -> {
                DefaultMacroProperty.ByName
            }
            Local -> localStorage[expression.path]
            Session -> sessionContext.sessionStorage[expression.path]
            Persistent -> sessionContext.persistentStorage?.get(expression.path)
            Environment -> sessionContext.environmentStorage[expression.path]
            is MacroValueStorageType.Custom -> {
                terminal.warning("Custom macro storage type '${type.key}' is currently not supported")
                null
            }
        }
    }

}
