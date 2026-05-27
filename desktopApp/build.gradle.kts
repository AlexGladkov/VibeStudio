import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // Coroutines Swing dispatcher (provides Dispatchers.Main on Desktop JVM)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    // Terminal emulation — PTY process management
    implementation("org.jetbrains.pty4j:pty4j:0.13.11")
    // Terminal emulation — JediTerm ANSI/VT100 emulator and Swing widget
    // Version 3.68 is the latest stable release on intellij-dependencies maven.
    // The original requirement of 3.72 does not exist upstream; 3.68 is the
    // correct artifact to use.
    implementation("org.jetbrains.jediterm:jediterm-core:3.68")
    implementation("org.jetbrains.jediterm:jediterm-ui:3.68")

    // Embedded HTTP server for Remote Control
    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("io.ktor:ktor-server-websockets:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
}

compose.desktop {
    application {
        mainClass = "studio.vibe.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VibeStudio"
            packageVersion = "1.0.0"
            description = "VibeStudio — AI-powered terminal IDE"
            vendor = "VibeStudio"

            macOS {
                bundleID = "studio.vibe.desktop"
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                dirChooser = true
                menuGroup = "VibeStudio"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
