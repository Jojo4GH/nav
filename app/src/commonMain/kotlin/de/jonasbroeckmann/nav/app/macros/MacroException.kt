package de.jonasbroeckmann.nav.app.macros

data class MacroException(
    val macroTraceContext: MacroTraceContext,
    override val cause: Exception? = null,
    override val message: String? = cause?.message
) : Throwable() {
    override fun toString() = buildString {
        append("Error while running macro")
        if (message != null) {
            append(": ")
            append(message)
        }
        if (cause != null) {
            append(" (")
            append(cause::class.simpleName)
            append(")")
        }
        appendLine()
        append(macroTraceContext.traceToString())
    }

    companion object {
        context(traceContext: MacroTraceContext)
        operator fun invoke(message: String) = MacroException(
            macroTraceContext = traceContext,
            message = message
        )

        context(traceContext: MacroTraceContext)
        operator fun invoke(cause: Exception, message: String? = cause.message) = MacroException(
            macroTraceContext = traceContext,
            cause = cause,
            message = message
        )

        inline fun handle(onException: (MacroException) -> Unit, block: context(MacroTraceContext) () -> Unit) {
            try {
                context(MacroTraceContext.Empty, block)
            } catch (e: MacroException) {
                onException(e)
            }
        }
    }
}
