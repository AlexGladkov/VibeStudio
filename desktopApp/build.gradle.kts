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
    implementation("io.ktor:ktor-server-cors:3.5.0")
    // JSON parsing for ngrok API response (no additional dep — org.json ships with Android/JVM)
    implementation("org.json:json:20240303")

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(compose.desktop.uiTestJUnit4)
}

val appVersion = project.findProperty("app.version")?.toString() ?: "1.0.0"

compose.desktop {
    application {
        mainClass = "studio.vibe.desktop.MainKt"

        jvmArgs += listOf(
            "-Xmx2g",
            "-Xms256m",
            "-XX:+UseG1GC",
            "-Dsun.java2d.opengl=true",
            "-Dapple.awt.application.appearance=system",
        )

        buildTypes.release {
            proguard {
                // Compose Desktop + reflection = ProGuard issues; disabled for now.
                // TODO: Enable with proper keep rules when ready.
                // Rules prepared in desktopApp/proguard-rules.pro.
                isEnabled.set(false)
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VibeStudio"
            packageVersion = appVersion
            description = "VibeStudio — AI-powered terminal IDE"
            vendor = "VibeStudio"
            copyright = "© 2024-2026 VibeStudio"
            licenseFile.set(rootProject.file("LICENSE"))

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
                debMaintainer = "dev@vibestudio.tech"
                appCategory = "Development"
            }
        }
    }
}
