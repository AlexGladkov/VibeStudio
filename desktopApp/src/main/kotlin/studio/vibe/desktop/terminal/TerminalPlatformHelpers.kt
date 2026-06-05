package studio.vibe.desktop.terminal

/** Returns `true` when running on a Windows OS. */
internal val isWindows: Boolean
    get() = System.getProperty("os.name", "").lowercase().contains("windows")

/** Returns `true` when running on macOS. */
internal val isMac: Boolean
    get() = System.getProperty("os.name", "").lowercase().contains("mac")

/**
 * Resolves the user's preferred shell.
 *
 * Priority:
 * 1. `SHELL` environment variable (set by the user's login session)
 * 2. macOS / Linux fallback: `/bin/zsh` on macOS, `/bin/bash` elsewhere
 * 3. Windows: `cmd.exe`
 */
internal fun resolveDefaultShell(): String {
    System.getenv("SHELL")?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        isWindows -> "cmd.exe"
        isMac -> "/bin/zsh"
        else -> "/bin/bash"
    }
}
