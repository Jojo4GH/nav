import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.crypto.checksum.Checksum
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.gmazzo.buildconfig)
    alias(libs.plugins.gradle.checksum)
    alias(libs.plugins.dorongold.tasktree)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "de.jonasbroeckmann.nav"
version = "1.2.1"

val mainClassJvm = "$group.MainKt"
val binaryName = "nav"

buildConfig {
    buildConfigField("String", "VERSION", "\"$version\"")
    buildConfigField("String", "BINARY_NAME", "\"$binaryName\"")
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
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
    }
}


val jvmPackageTasks = kotlin.targets.withType<KotlinJvmTarget>().map { jvmTarget ->
    val targetName = jvmTarget.name
    val main by jvmTarget.compilations.named("main")
    val jvmShadowJar = tasks.register<ShadowJar>("${targetName}ShadowJar") {
        group = "build"
        from(main.output)
        configurations = listOf(main.runtimeDependencyFiles)
        manifest {
            attributes("Main-Class" to mainClassJvm)
        }
        archiveFileName.set("$binaryName-$targetName.jar")
    }

    tasks.register<Copy>("package${targetName.replaceFirstChar { it.uppercase() }}") {
        group = "distribution"
        dependsOn(jvmShadowJar)
        from(jvmShadowJar)
        into(layout.buildDirectory.dir("packages"))
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

val nativePackageTasks = tasks.withType<KotlinNativeLink>().filter { it.optimized }.map { linkTask ->
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

val packageTasks = jvmPackageTasks + nativePackageTasks

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

