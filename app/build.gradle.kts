plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // alias(libs.plugins.kotlin.jvm)
    // alias(libs.plugins.compose.compiler)
}

group = "de.jonasbroeckmann.nav"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers"
        )
    }

    jvm {
        mainRun {
            mainClass.set("$group.MainKt")
        }
    }

    linuxX64 {
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
                implementation(libs.kotlinx.io)

                implementation(libs.mordant)
                implementation(libs.mordant.coroutines)
            }
        }
        jvmMain {

        }
        nativeMain {

        }
    }
}

//dependencies {
//    implementation(libs.kotlinx.coroutines)
//}


//application {
//    mainClass.set("$group.MainKt")
//}

//distributions {
//    this.
//}

