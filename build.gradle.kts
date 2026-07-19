plugins {
    val kotlinVersion = "2.4.10"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("dev.detekt") version "2.0.0-alpha.0" apply false
    id("com.codingfeline.buildkonfig") version "0.22.0" apply false
    id("org.gradle.crypto.checksum") version "1.4.0" apply false
    id("com.netflix.nebula.ospackage") version "12.3.0" apply false
}
