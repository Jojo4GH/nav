import org.gradle.crypto.checksum.Checksum
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.gmazzo.buildconfig)
    alias(libs.plugins.gradle.checksum)
    alias(libs.plugins.dorongold.tasktree)
}

group = "de.jonasbroeckmann.nav"
version = "1.0.0"

val binaryName = "nav"

buildConfig {
    buildConfigField("String", "VERSION", "\"$version\"")
    buildConfigField("String", "BINARY_NAME", "\"$binaryName\"")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers"
        )
    }

    linuxX64()
    linuxArm64()
    mingwX64 {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xdisable-phases=EscapeAnalysis" // prevent OutOfMemoryError during escape analysis
            )
        }
    }
//    macosX64()

    targets.withType<KotlinNativeTarget> {
        binaries {
            executable {
                baseName = binaryName
                entryPoint = "$group.main"
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.io)

                implementation(libs.ktoml.core)
                implementation(libs.ktoml.file)

                implementation(libs.clikt)

                implementation(libs.mordant)
                implementation(libs.mordant.coroutines)

                implementation(libs.kommand)
            }
        }
        nativeMain {

        }
    }
}



tasks.withType<KotlinNativeLink>().filter { it.optimized }.forEach { linkTask ->
    val taskName = linkTask.name.removePrefix("link")

    tasks.register<Zip>("package$taskName") {
        group = "distribution"
        dependsOn(linkTask)
        from(linkTask.outputFile) {
            rename("nav\\.kexe", "nav")
        }
        destinationDirectory.set(layout.buildDirectory.dir("packages"))
        archiveFileName.set(listOfNotNull(
            "nav",
            linkTask.binary.compilation.konanTarget.targetTriple,
            if (!linkTask.optimized) "debug" else null
        ).joinToString("-", postfix = ".zip"))
    }.get()

    tasks.register<Checksum>("checksum$taskName") {
        group = "distribution"
        val packageTask = tasks.named("package$taskName")
        mustRunAfter(packageTask)
        inputFiles.from(packageTask)
        checksumAlgorithm.set(Checksum.Algorithm.SHA256)
    }
}

tasks.register("packageAll") {
    group = "distribution"
    dependsOn(tasks.withType<Zip>().filter { it.name.startsWith("package") })
    dependsOn(tasks.withType<Checksum>().filter { it.name.startsWith("checksum") })
}


val KonanTarget.targetTriple: String get() = listOfNotNull(
    when (architecture) {
        Architecture.X64 -> "x86_64"
        Architecture.X86 -> "i686"
        Architecture.ARM64 -> "aarch64"
        Architecture.ARM32 -> "armv7k"
    },
    when (family) {
        Family.OSX -> "apple"
        Family.IOS -> "apple"
        Family.TVOS -> "apple"
        Family.WATCHOS -> "apple"
        Family.LINUX -> "unknown"
        Family.MINGW -> "pc"
        Family.ANDROID -> "unknown"
    },
    when (family) {
        Family.OSX -> "macos"
        Family.IOS -> "ios"
        Family.TVOS -> "tvos"
        Family.WATCHOS -> "watchos"
        Family.LINUX -> "linux-gnu"
        Family.MINGW -> "windows-gnu"
        Family.ANDROID -> when (this) {
            KonanTarget.ANDROID_ARM32 -> "linux-androideabi"
            else -> "linux-android"
        }
    },
    when (this) {
        KonanTarget.IOS_SIMULATOR_ARM64 -> "simulator"
        KonanTarget.IOS_X64 -> "simulator"
        KonanTarget.TVOS_SIMULATOR_ARM64 -> "simulator"
        KonanTarget.TVOS_X64 -> "simulator"
        KonanTarget.WATCHOS_SIMULATOR_ARM64 -> "simulator"
        KonanTarget.WATCHOS_X64 -> "simulator"
        else -> null
    }
).joinToString("-")

