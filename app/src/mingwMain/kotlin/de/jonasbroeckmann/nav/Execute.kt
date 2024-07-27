package de.jonasbroeckmann.nav



actual fun execute(command: String, vararg args: String): Int {
    // TODO bad
    val escaped = args.joinToString(" ") { "\"$it\"" }
    return platform.posix.system("$command $escaped")
}

