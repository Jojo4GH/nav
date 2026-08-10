package de.jonasbroeckmann.nav

import io.kotest.core.spec.style.FunSpec

abstract class TestSpec(body: context(Logger) FunSpec.() -> Unit = {}) : FunSpec({ context(TestLogger) { body() } })
