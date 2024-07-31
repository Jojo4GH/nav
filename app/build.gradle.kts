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
version = "1.1.0"

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


inline fun <reified T : AbstractArchiveTask> TaskContainer.registerPackage(
    linkTask: KotlinNativeLink,
    name: String,
    extension: String,
    crossinline block: T.() -> Unit = {}
) = register<T>("package$name") {
    group = "distribution"
    dependsOn(linkTask)
    from(linkTask.outputFile) {
        rename("nav\\.kexe", "nav")
        filePermissions { unix("rwxr-xr-x") }
    }
    destinationDirectory.set(layout.buildDirectory.dir("packages"))
    archiveFileName.set(
        listOfNotNull(
            "nav",
            linkTask.binary.compilation.konanTarget.targetTriple,
            if (!linkTask.optimized) "debug" else null
        ).joinToString("-", postfix = extension)
    )
    block()
}

tasks.withType<KotlinNativeLink>().filter { it.optimized }.forEach { linkTask ->
    val konanTarget = linkTask.binary.compilation.konanTarget
    val taskName = linkTask.name.removePrefix("link")

    val packageTask = if (konanTarget.family == Family.MINGW) {
        tasks.registerPackage<Zip>(linkTask, taskName, ".zip")
    } else {
        tasks.registerPackage<Tar>(linkTask, taskName, ".tar.gz") {
            compression = Compression.GZIP
        }
    }

    tasks.register<Checksum>("checksum$taskName") {
        group = "distribution"
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

