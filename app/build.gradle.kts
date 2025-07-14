import org.gradle.crypto.checksum.Checksum
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    val kotlinVersion = "2.2.0"
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("com.github.gmazzo.buildconfig") version "5.6.7"
    id("org.gradle.crypto.checksum") version "1.4.0"
}

group = "de.jonasbroeckmann.nav"
version = "1.3.2"

val binaryName = "nav"

buildConfig {
    buildConfigField("String", "VERSION", "\"$version\"")
    buildConfigField("String", "BINARY_NAME", "\"$binaryName\"")
}

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
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.0")

            val ktomlVersion = "0.7.0"
            implementation("com.akuleshov7:ktoml-core:$ktomlVersion")
            implementation("com.akuleshov7:ktoml-file:$ktomlVersion")

            implementation("com.github.ajalt.clikt:clikt:5.0.3")

            val mordantVersion = "3.0.2"
            implementation("com.github.ajalt.mordant:mordant:$mordantVersion")
            implementation("com.github.ajalt.mordant:mordant-coroutines:$mordantVersion")

            implementation("com.kgit2:kommand:2.3.0")
        }
    }
}

inline fun <reified T : AbstractArchiveTask> TaskContainer.registerPackage(
    linkTask: KotlinNativeLink,
    name: String,
    extension: String,
    crossinline block: T.() -> Unit = {}
) = register<T>("package$name") {
    group = "distribution"
    dependsOn(linkTask)
    from(linkTask.outputFile) {
        rename("$binaryName\\.kexe", binaryName)
        filePermissions { unix("rwxr-xr-x") }
    }
    destinationDirectory.set(layout.buildDirectory.dir("packages"))
    archiveFileName.set(
        listOfNotNull(
            binaryName,
            linkTask.binary.compilation.konanTarget.targetTriple,
            if (!linkTask.optimized) "debug" else null
        ).joinToString("-", postfix = extension)
    )
    block()
}

val packageTasks = tasks.withType<KotlinNativeLink>().filter { it.optimized }.map { linkTask ->
    val konanTarget = linkTask.binary.compilation.konanTarget
    val taskName = linkTask.name.removePrefix("link")

    if (konanTarget.family == Family.MINGW) {
        tasks.registerPackage<Zip>(linkTask, taskName, ".zip")
    } else {
        tasks.registerPackage<Tar>(linkTask, taskName, ".tar.gz") {
            compression = Compression.GZIP
        }
    }
}

val checksumTask = tasks.register<Checksum>("checksums") {
    group = "distribution"
    mustRunAfter(packageTasks)
    inputFiles.from(packageTasks)
    outputDirectory.set(layout.buildDirectory.dir("checksums"))
    checksumAlgorithm.set(Checksum.Algorithm.SHA256)
}

tasks.register("packageAll") {
    group = "distribution"
    dependsOn(packageTasks)
    dependsOn(checksumTask)
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
