plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("co.touchlab.skie") version "0.10.12"
}

kotlin {
    jvm()

    macosArm64 {
        binaries.framework {
            baseName = "VibeStudioShared"
            isStatic = true
        }
    }
    macosX64 {
        binaries.framework {
            baseName = "VibeStudioShared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("io.ktor:ktor-client-core:3.5.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.5.0")
            implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
        }

        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.5.0")
        }

        val macosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.5.0")
            }
        }
        val macosArm64Main by getting { dependsOn(macosMain) }
        val macosX64Main by getting { dependsOn(macosMain) }

        val macosTest by creating {
            dependsOn(commonTest.get())
        }
        val macosArm64Test by getting { dependsOn(macosTest) }
        val macosX64Test by getting { dependsOn(macosTest) }
    }
}
