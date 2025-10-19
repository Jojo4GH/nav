package de.jonasbroeckmann.nav.utils

import com.github.ajalt.mordant.animation.coroutines.CoroutineAnimator
import com.github.ajalt.mordant.animation.progress.ProgressTask
import com.github.ajalt.mordant.terminal.TerminalInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

expect fun customTerminalInterface(): TerminalInterface?

context(coroutineScope: CoroutineScope)
inline fun <A, R> A.executeWhile(block: (A) -> R): R where A : CoroutineAnimator, A : ProgressTask<*> {
    coroutineScope.launch { execute() }
    try {
        return block(this)
    } finally {
        update {
            total = 1
            completed = 1
        }
    }
}
