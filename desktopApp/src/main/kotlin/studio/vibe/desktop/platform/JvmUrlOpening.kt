package studio.vibe.desktop.platform

import studio.vibe.shared.core.common.UrlOpening

/** JVM singleton — opens URLs in the AWT Desktop browser. */
internal object JvmUrlOpening : UrlOpening {
    override fun openUrl(url: String) {
        try {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            }
        } catch (_: Exception) { /* Ignore — best effort */ }
    }
}
