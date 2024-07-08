package org.example

actual val platform: String = "JVM on ${System.getProperty("os.name")} on ${System.getProperty("os.arch")}"
