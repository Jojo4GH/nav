package de.jonasbroeckmann.nav.framework.utils

fun Iterable<String>.commonPrefix(): String {
    val iter = iterator()
    if (!iter.hasNext()) return ""
    var prefix = iter.next()
    while (iter.hasNext()) {
        val next = iter.next()
        prefix = prefix.commonPrefixWith(next)
    }
    return prefix
}
