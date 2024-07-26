plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
}

group = "de.jonasbroeckmann.nav"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers"
        )
    }

//    jvm {
//        mainRun {
//            mainClass.set("$group.MainKt")
//        }
//    }

    linuxX64 {
        binaries {
            executable {
                entryPoint = "$group.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                entryPoint = "$group.main"
            }
        }
    }
    mingwX64 {
        binaries {
            executable {
                entryPoint = "$group.main"
            }
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

//                implementation("com.akuleshov7:ktoml-core:0.5.1")
//                implementation("com.akuleshov7:ktoml-file:0.5.1")

                implementation(libs.clikt)

                implementation(libs.mordant)
                implementation(libs.mordant.coroutines)
            }
        }
        nativeMain {

        }
    }
}

//distributions {
//
//}

