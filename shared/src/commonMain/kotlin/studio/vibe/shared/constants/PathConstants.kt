package studio.vibe.shared.constants

public object PathConstants {
    public val excludedDirectoryNames: Set<String> = setOf(
        ".git", ".build", ".swiftpm", ".idea", ".gradle",
        ".DS_Store", "__pycache__", "build", "DerivedData",
        "node_modules", "Pods", "target",
    )

    public val forbiddenRootPaths: Set<String> = setOf(
        "/", "/System", "/Library", "/usr", "/bin",
        "/sbin", "/etc", "/dev", "/tmp", "/private", "/var",
    )
}
