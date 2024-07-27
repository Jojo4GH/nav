package de.jonasbroeckmann.nav

import kotlinx.cinterop.*
import platform.posix.*


@OptIn(ExperimentalForeignApi::class)
actual fun execute(command: String, vararg args: String): Int = memScoped {
    val pid = fork()
    if (pid == 0) {
        val result = execvp(command, args.map { it.cstr.ptr }.toCValues())
        throw AssertionError("execvp failed ($result)")
    } else {
        val status = alloc<Int>(0)
        waitpid(pid, status.ptr, 0)
        status.value
    }
}
