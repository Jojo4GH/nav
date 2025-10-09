package de.jonasbroeckmann.nav.app.actions

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.TextStyle

sealed interface Action<Context, Input : InputEvent?, Output> {
    context(context: Context)
    fun description(): String

    context(context: Context)
    fun style(): TextStyle?

    context(context: Context)
    fun isHidden(): Boolean

    context(context: Context)
    infix fun matches(input: Input): Boolean

    context(context: Context)
    fun isAvailable(): Boolean

    context(context: Context)
    fun isShown() = !isHidden() && isAvailable()

    context(context: Context)
    fun run(input: Input): Output
}
