package de.jonasbroeckmann.nav.framework.ui

import de.jonasbroeckmann.nav.framework.action.KeyAction
import de.jonasbroeckmann.nav.framework.input.InputMode
import kotlin.to

fun buildHints(
    defaultStrongSpacing: String,
    defaultWeakSpacing: String = " ",
    spacingMergeStrategy: HintsBuilder.SpacingMergeStrategy = HintsBuilder.SpacingMergeStrategy.MergeNext,
    block: HintsBuilder.() -> Unit
) = HintsBuilder(
    defaultStrongSpacing = defaultStrongSpacing,
    defaultWeakSpacing = defaultWeakSpacing,
    spacingMergeStrategy = spacingMergeStrategy
).apply(block).build()

@DslMarker
annotation class HintsBuilderDsl

@HintsBuilderDsl
class HintsBuilder internal constructor(
    private val defaultStrongSpacing: String,
    private val defaultWeakSpacing: String,
    private val spacingMergeStrategy: SpacingMergeStrategy
) {
    enum class SpacingMergeStrategy {
        NoMerge,
        MergeNext,
        MergePrevious
    }

    private enum class ElementType(val isSpacing: Boolean) {
        WithWeakSpacing(false),
        WithStrongSpacing(false),
        WeakSpacing(true),
        StrongSpacing(true)
    }

    private val elements = mutableListOf<Pair<ElementType, () -> String>>()

    internal fun build(): Sequence<String> = sequence {
        var lastElementType: ElementType? = null
        elements.forEachIndexed { index, (type, element) ->
            when (type) {
                StrongSpacing -> {
                    yield(type to element())
                    lastElementType = ElementType.StrongSpacing
                }
                WeakSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing && index < elements.lastIndex) {
                        yield(type to element())
                        lastElementType = ElementType.WeakSpacing
                    }
                }
                WithStrongSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing) {
                        yield(ElementType.StrongSpacing to defaultStrongSpacing)
                    }
                    yield(type to element())
                    lastElementType = ElementType.WithStrongSpacing
                }
                WithWeakSpacing -> {
                    if (lastElementType != null && !lastElementType.isSpacing) {
                        if (lastElementType == ElementType.WithWeakSpacing) {
                            yield(ElementType.WeakSpacing to defaultWeakSpacing)
                        } else {
                            yield(ElementType.StrongSpacing to defaultStrongSpacing)
                        }
                    }
                    yield(type to element())
                    lastElementType = ElementType.WithWeakSpacing
                }
            }
        }
    }.applySpacingMergeStrategy()

    private fun Sequence<Pair<ElementType, String>>.applySpacingMergeStrategy(): Sequence<String> = when (spacingMergeStrategy) {
        NoMerge -> map { (_, element) -> element }
        MergeNext -> sequence {
            var spacingBuffer: String? = null
            forEach { (type, element) ->
                if (type.isSpacing) {
                    spacingBuffer = spacingBuffer.orEmpty() + element
                } else {
                    yield(spacingBuffer.orEmpty() + element)
                    spacingBuffer = null
                }
            }
            if (spacingBuffer != null) {
                yield(spacingBuffer)
            }
        }
        MergePrevious -> sequence {
            var elementBuffer: String? = null
            forEach { (type, element) ->
                if (type.isSpacing) {
                    yield(elementBuffer.orEmpty() + element)
                    elementBuffer = null
                } else {
                    if (elementBuffer != null) {
                        yield(elementBuffer)
                    }
                    elementBuffer = element
                }
            }
            if (elementBuffer != null) {
                yield(elementBuffer)
            }
        }
    }

    private fun add(type: ElementType, element: () -> String) {
        elements += type to element
    }

    fun addSpacing(weak: Boolean = false, render: () -> String = { if (weak) defaultWeakSpacing else defaultStrongSpacing }) {
        add(if (weak) ElementType.WeakSpacing else ElementType.StrongSpacing, render)
    }

    fun add(weakSpacing: Boolean = false, render: () -> String) {
        add(if (weakSpacing) ElementType.WithWeakSpacing else ElementType.WithStrongSpacing, render)
    }

    fun <Context, A : KeyAction<Context, *>> addAction(
        action: A,
        context: Context,
        inputMode: InputMode?,
        weakSpacing: Boolean = false,
        render: context(Context) A.() -> String
    ): Unit = context(context) {
        if (!action.isShown(inputMode)) return
        add(weakSpacing) { action.render() }
    }

    fun <Context, A : KeyAction<Context, *>> addActions(
        actions: Iterable<A>,
        context: Context,
        inputMode: InputMode?,
        weakSpacing: Boolean = false,
        render: context(Context) A.() -> String
    ) {
        actions.forEach {
            addAction(
                action = it,
                context = context,
                inputMode = inputMode,
                weakSpacing = weakSpacing,
                render = render
            )
        }
    }
}
