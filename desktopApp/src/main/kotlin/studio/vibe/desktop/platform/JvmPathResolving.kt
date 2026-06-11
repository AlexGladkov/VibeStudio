package studio.vibe.desktop.platform

import studio.vibe.shared.core.common.PathResolving

/** JVM singleton — reads the user home from system properties. */
internal object JvmPathResolving : PathResolving {
    override fun resolveHomePath(): String = System.getProperty("user.home") ?: ""
}
