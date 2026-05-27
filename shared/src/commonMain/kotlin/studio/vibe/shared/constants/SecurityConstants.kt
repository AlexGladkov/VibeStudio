package studio.vibe.shared.constants

public object SecurityConstants {
    public fun trustedBinDirectories(homeDir: String): List<String> = listOf(
        "/opt/homebrew/bin",
        "/opt/homebrew/sbin",
        "/usr/local/bin",
        "$homeDir/.local/bin",
        "$homeDir/.npm-global/bin",
        "$homeDir/.cargo/bin",
        "$homeDir/.opencode/bin",
        "/usr/bin",
    )
}
