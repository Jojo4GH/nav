package de.jonasbroeckmann.nav.app.ui

import de.jonasbroeckmann.nav.app.actions.KeyAction
import de.jonasbroeckmann.nav.config.StylesProvider

context(stylesProvider: StylesProvider)
inline fun <Context> buildHints(
    defaultStrongSpacing: String = stylesProvider.styles.genericElements(" • "),
    block: HintsBuilder<Context>.() -> Unit
): String {
    return HintsBuilder<Context>(defaultStrongSpacing).apply(block).render()
}

class HintsBuilder<Context>(
    private val defaultStrongSpacing: String
) {
    private enum class ElementType(val isSpacing: Boolean) {
        WithWeakSpacing(false), WithStrongSpacing(false), WeakSpacing(true), StrongSpacing(true)
    }

    private val elements = mutableListOf<Pair<ElementType, () -> String>>()

    fun render(): String = buildString {
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

    context(stylesProvider: StylesProvider)
    fun addAction(action: KeyAction<Context, *>, context: Context, weakSpacing: Boolean = false) {
        if (context(context) { !action.isShown() }) return
        add(weakSpacing) { renderAction(action, context) }
    }

    context(stylesProvider: StylesProvider)
    fun addActions(actions: Iterable<KeyAction<Context, *>>, context: Context, weakSpacing: Boolean = false) {
        actions.forEach { addAction(it, context, weakSpacing) }
    }
}
