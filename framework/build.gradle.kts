import dev.detekt.gradle.Detekt
import org.gradle.kotlin.dsl.withType

plugins {
    kotlin("multiplatform")
    id("dev.detekt")
}

group = "de.jonasbroeckmann.nav"

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xcontext-sensitive-resolution",
            "-Xnested-type-aliases",
            "-Xconsistent-data-class-copy-visibility"
        )
    }

    jvm()

    linuxX64()
    linuxArm64()
    mingwX64()
//    macosX64()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            val kotlinxIOVersion = "0.8.0"
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:$kotlinxIOVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-io-okio:$kotlinxIOVersion")

            implementation("com.github.ajalt.clikt:clikt:5.0.3")

            val mordantVersion = "3.0.2"
            implementation("com.github.ajalt.mordant:mordant:$mordantVersion")
            implementation("com.github.ajalt.mordant:mordant-coroutines:$mordantVersion")

            implementation("com.kgit2:kommand:2.3.0")
        }
    }
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.0")
}

tasks.register("detektAll") {
    group = "verification"
    description = "Run all detekt checks"
    dependsOn(tasks.withType<Detekt>().filter { !it.multiPlatformEnabled.get() })
}
