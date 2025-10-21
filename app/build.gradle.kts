import com.netflix.gradle.plugins.deb.Deb
import dev.detekt.gradle.Detekt
import org.gradle.crypto.checksum.Checksum
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.*
import org.jetbrains.kotlin.konan.target.Architecture

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("dev.detekt")
    id("com.github.gmazzo.buildconfig")
    id("org.gradle.crypto.checksum")
    id("com.netflix.nebula.ospackage")
}

group = "de.jonasbroeckmann.nav"
version = "1.5.0"

description = "The interactive and stylish replacement for ls & cd!"

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
            "-Xconsistent-data-class-copy-visibility",
            "-Xallow-holdsin-contract",
        )
    }

    jvm()

    linuxX64()
    linuxArm64()
    mingwX64()
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
        val ktorVersion = "3.3.1"

        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            val kotlinxIOVersion = "0.8.0"
            implementation("org.jetbrains.kotlinx:kotlinx-io-core:$kotlinxIOVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-io-okio:$kotlinxIOVersion")

            val ktomlVersion = "0.7.1"
            implementation("com.akuleshov7:ktoml-core:$ktomlVersion")
            implementation("com.akuleshov7:ktoml-file:$ktomlVersion")

            implementation("com.charleskorn.kaml:kaml:0.97.0")

            implementation("com.github.ajalt.clikt:clikt:5.0.3")

            val mordantVersion = "3.0.2"
            implementation("com.github.ajalt.mordant:mordant:$mordantVersion")
            implementation("com.github.ajalt.mordant:mordant-coroutines:$mordantVersion")
            implementation("com.github.ajalt.mordant:mordant-markdown:$mordantVersion")

            implementation("com.kgit2:kommand:2.3.0")

            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

            api(projects.framework)
        }

        linuxMain.dependencies {
            implementation("io.ktor:ktor-client-curl:$ktorVersion")
        }

        mingwMain.dependencies {
            implementation("io.ktor:ktor-client-winhttp:$ktorVersion")
        }
    }
}

dependencies {
    detektPlugins("dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.0")
}

tasks.withType<Detekt>().configureEach {
    exclude("de/jonasbroeckmann/nav/app/BuildConfig.kt")
}

tasks.register("detektAll") {
    group = "verification"
    description = "Run all detekt checks"
    dependsOn(tasks.withType<Detekt>().filter { !it.multiPlatformEnabled.get() })
}

abstract class VerifyVersion : DefaultTask() {
    @Option(option = "against", description = "The version to verify")
    @Optional
    @Input
    var verifyVersion: String? = null

    @Input
    lateinit var projectVersion: String

    @InputFile
    lateinit var pkgbuildFile: File

    @TaskAction
    fun verify() {
        if (verifyVersion != null) {
            require(projectVersion == verifyVersion) {
                "Project version '$projectVersion' does not match the provided version '$verifyVersion'"
            }
        }
        pkgbuildFile.useLines { lines ->
            val (pkgver) = lines
                .mapNotNull { line -> Regex("""^pkgver="(.*)"$""").matchEntire(line) }
                .singleOrNull()
                ?.destructured
                ?: error("Could not find exactly one version line in install/PKGBUILD")
            require(pkgver == projectVersion) {
                "PKGBUILD version '$pkgver' does not match the project version '$projectVersion'"
            }
        }
        println("Version '$projectVersion' verified successfully")
    }
}

tasks.register<VerifyVersion>("verifyVersion") {
    group = "verification"
    projectVersion = project.version.toString()
    pkgbuildFile = rootProject.file("install/PKGBUILD")
}

inline fun <reified T : AbstractArchiveTask> TaskContainer.registerPackage(
    linkTask: KotlinNativeLink,
    name: String,
    extension: String,
    deb: Boolean = false,
    crossinline block: T.() -> Unit = {}
) = buildList {
    val target = linkTask.binary.compilation.konanTarget

    this += register<T>("package$name") {
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
                target.targetTriple,
                if (!linkTask.optimized) "debug" else null
            ).joinToString("-", postfix = extension)
        )
        block()
    }

    if (deb) {
        this += register<Deb>("package${name}Deb") {
            group = "distribution"
            dependsOn(linkTask)
            packageName = "nav"
            archStr = when (target.architecture) {
                Architecture.X64 -> "amd64"
                Architecture.X86 -> "i386"
                Architecture.ARM64 -> "arm64"
                Architecture.ARM32 -> "armhf"
            }
            maintainer = "Jojo4GH" // TODO email
            url = "https://github.com/Jojo4GH/nav"
            packageGroup = "utils"
            summary = project.description
            packageDescription = null // TODO long description
            from(linkTask.outputFile) {
                rename("$binaryName\\.kexe", binaryName)
                filePermissions { unix("rwxr-xr-x") }
                into("/usr/bin")
            }
            postInstall("/usr/bin/$binaryName --init-help")
            destinationDirectory.set(layout.buildDirectory.dir("packages"))
            archiveFileName.set("${packageName}_$archStr.deb")
        }
    }
}

val packageTasks = tasks.withType<KotlinNativeLink>().filter { it.optimized }.flatMap { linkTask ->
    val konanTarget = linkTask.binary.compilation.konanTarget
    val taskName = linkTask.name.removePrefix("link")

    when (konanTarget.family) {
        Family.MINGW -> tasks.registerPackage<Zip>(
            linkTask = linkTask,
            name = taskName,
            extension = ".zip"
        )
        else -> tasks.registerPackage<Tar>(
            linkTask = linkTask,
            name = taskName,
            extension = ".tar.gz",
            deb = konanTarget.family == Family.LINUX
        ) {
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
