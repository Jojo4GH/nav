import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.gmazzo.buildconfig)
}

group = "de.jonasbroeckmann.nav"
version = "1.0.0"

buildConfig {
    buildConfigField("String", "VERSION", "\"$version\"")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers"
        )
    }

    linuxX64 {
        binaries {
            executable {
                baseName = "nav"
                entryPoint = "$group.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                baseName = "nav"
                entryPoint = "$group.main"
            }
        }
    }
    mingwX64 {
        binaries {
            executable {
                baseName = "nav"
                entryPoint = "$group.main"
            }
        }
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xdisable-phases=EscapeAnalysis" // prevent OutOfMemoryError during escape analysis
            )
        }
    }
//    macosX64 {
//        binaries {
//            executable {
//                entryPoint = "$group.main"
//            }
//        }
//    }

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
    val targetName = linkTask.target.split("_").joinToString("") { part ->
        part.replaceFirstChar { it.uppercase() }
    }.replaceFirstChar { it.lowercase() }
    tasks.register<Zip>("package${linkTask.name.removePrefix("link")}") {
        group = "distribution"
        dependsOn(linkTask)
        from(linkTask.outputFile) {
            rename("nav\\.kexe", "nav")
        }
        destinationDirectory.set(layout.buildDirectory.dir("packaged"))
        archiveFileName.set(listOfNotNull(
            "nav",
            "v$version",
            targetName,
            if (!linkTask.optimized) "debug" else null
        ).joinToString("-", postfix = ".zip"))
    }
}

tasks.register("packageAll") {
    group = "distribution"
    dependsOn(tasks.withType<Zip>().filter { it.name.startsWith("package") })
}

