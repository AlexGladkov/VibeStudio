package studio.vibe.shared

actual fun platformName(): String = "JVM (${System.getProperty("os.name")})"
