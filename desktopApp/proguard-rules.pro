# =============================================================================
# VibeStudio Desktop — ProGuard Rules
#
# ProGuard is currently DISABLED in build.gradle.kts (isEnabled.set(false)).
# These rules are prepared for when it is enabled.
# Enable by setting isEnabled.set(true) in the buildTypes.release { proguard { } } block.
# =============================================================================

# -----------------------------------------------------------------------------
# Jetpack Compose / Compose Desktop runtime
# -----------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**

# Compose compiler-generated classes (stability metadata, group tables)
-keep class **ComposableSingletons* { *; }
-keep class **Kt$* { *; }

# -----------------------------------------------------------------------------
# Ktor (server + serialization)
# -----------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Netty (used by ktor-server-netty)
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# -----------------------------------------------------------------------------
# kotlinx.serialization
# -----------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Keep @Serializable annotated classes (generated serializers rely on reflection)
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** serializer(...);
    <fields>;
}

# -----------------------------------------------------------------------------
# kotlinx.coroutines
# -----------------------------------------------------------------------------
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# -----------------------------------------------------------------------------
# Terminal emulation — JediTerm
# -----------------------------------------------------------------------------
-keep class com.jediterm.** { *; }
-keepclassmembers class com.jediterm.** { *; }
-dontwarn com.jediterm.**

# -----------------------------------------------------------------------------
# Terminal emulation — pty4j
# -----------------------------------------------------------------------------
-keep class com.pty4j.** { *; }
-keepclassmembers class com.pty4j.** { *; }
-dontwarn com.pty4j.**

# Also keep native library loader helpers
-keep class org.jetbrains.pty4j.** { *; }
-dontwarn org.jetbrains.pty4j.**

# -----------------------------------------------------------------------------
# Application entry point
# -----------------------------------------------------------------------------
-keep class studio.vibe.desktop.MainKt { *; }
-keep class studio.vibe.desktop.** { *; }

# -----------------------------------------------------------------------------
# Kotlin reflection & metadata
# -----------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepattributes Signature, Exceptions, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# -----------------------------------------------------------------------------
# General safety rules
# -----------------------------------------------------------------------------
# Do not obfuscate exception messages (helps with crash reporting)
-keepattributes SourceFile, LineNumberTable

# Suppress notes for common "harmless" dynamic references
-dontnote android.**
-dontwarn android.**
