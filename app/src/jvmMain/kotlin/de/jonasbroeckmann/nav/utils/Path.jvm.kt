package de.jonasbroeckmann.nav.utils

import java.io.File

actual val PathsSeparator: Char get() = File.pathSeparatorChar

actual val RealSystemPathSeparator: Char get() = File.separatorChar
