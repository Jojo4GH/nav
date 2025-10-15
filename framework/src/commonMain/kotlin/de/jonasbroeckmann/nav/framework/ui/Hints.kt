package de.jonasbroeckmann.nav.framework.ui

import de.jonasbroeckmann.nav.framework.action.KeyAction

fun buildHints(
    defaultStrongSpacing: String,
    block: HintsBuilder.() -> Unit
) = HintsBuilder(defaultStrongSpacing).apply(block).build()

@DslMarker
annotation class HintsBuilderDsl

@HintsBuilderDsl
class HintsBuilder internal constructor(private val defaultStrongSpacing: String) {
    private enum class ElementType(val isSpacing: Boolean) {
        WithWeakSpacing(false),
        WithStrongSpacing(false),
        WeakSpacing(true),
        StrongSpacing(true)
    }

    private val elements = mutableListOf<Pair<ElementType, () -> String>>()

    internal fun build(): String = buildString {
        var lastElementType: ElementType? = null
        elements.forEachIndexed { index, (type, element) ->
            when (type) {
                ElementType.StrongSpacing -> {
                    append(element())
                    lastElementType = ElementType.StrongSpacing
                }
                ElementType.WeakSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing && index < elements.lastIndex) {
                        append(element())
                        lastElementType = ElementType.WeakSpacing
                    }
                }
                ElementType.WithStrongSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing) {
                        append(defaultStrongSpacing)
                    }
                    append(element())
                    lastElementType = ElementType.WithStrongSpacing
                }
                ElementType.WithWeakSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing) {
                        if (lastElementType == ElementType.WithWeakSpacing) {
                            append(" ")
                        } else {
                            append(defaultStrongSpacing)
                        }
                    }
                    append(element())
                    lastElementType = ElementType.WithWeakSpacing
                }
            }
        }
    }

    private fun add(type: ElementType, element: () -> String) {
        elements += type to element
    }

    fun addSpacing(weak: Boolean = false, render: () -> String = { defaultStrongSpacing }) {
        add(if (weak) ElementType.WeakSpacing else ElementType.StrongSpacing, render)
    }

    fun add(weakSpacing: Boolean = false, render: () -> String) {
        add(if (weakSpacing) ElementType.WithWeakSpacing else ElementType.WithStrongSpacing, render)
    }

    fun <Context, A : KeyAction<Context, *>> addAction(
        action: A,
        context: Context,
        weakSpacing: Boolean = false,
        render: context(Context) A.() -> String
    ) = context(context) {
        if (!action.isShown()) return
        add(weakSpacing) { action.render() }
    }

    fun <Context, A : KeyAction<Context, *>> addActions(
        actions: Iterable<A>,
        context: Context,
        weakSpacing: Boolean = false,
        render: context(Context) A.() -> String
    ) {
        actions.forEach {
            addAction(
                action = it,
                context = context,
                weakSpacing = weakSpacing,
                render = render
            )
        }
    }
}
