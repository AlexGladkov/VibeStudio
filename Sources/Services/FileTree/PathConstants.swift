// MARK: - PathConstants
// Centralised path lists for file operations, watchers, and project validation.
// macOS 14+, Swift 5.10

import Foundation

/// Centralised path constants used across file tree building,
/// file system watching, and project management.
///
/// Keeping these in one place prevents divergence between subsystems
/// and makes adding new exclusions a single-line change.
enum PathConstants {

    /// Directory names excluded from the file tree and FSEvent monitoring.
    ///
    /// Any directory whose `lastPathComponent` matches one of these
    /// entries is skipped during tree building and filtered out of
    /// file-system change events.
    static let excludedDirectoryNames: Set<String> = [
        ".git",
        ".build",
        ".swiftpm",
        ".idea",
        ".gradle",
        ".DS_Store",
        "__pycache__",
        "build",
        "DerivedData",
        "node_modules",
        "Pods",
        "target",
    ]

    /// Absolute paths that must never be opened as a project root
    /// or monitored by the file system watcher.
    ///
    /// Attempting to watch or add these paths is rejected immediately
    /// to prevent accidental system-wide scanning.
    static let forbiddenRootPaths: Set<String> = [
        "/",
        "/System",
        "/Library",
        "/usr",
        "/bin",
        "/sbin",
        "/etc",
        "/dev",
        "/tmp",
        "/private",
        "/var",
    ]

    /// `~/Library/Application Support/VibeStudio` directory URL.
    ///
    /// Creates the directory with owner-only permissions (0o700)
    /// if it does not already exist.
    ///
    /// - Throws: If the Application Support base directory cannot be located.
    static var appSupportDirectory: URL {
        get throws {
            guard let base = FileManager.default.urls(
                for: .applicationSupportDirectory,
                in: .userDomainMask
            ).first else {
                throw PathConstantsError.appSupportNotFound
            }

            let appDir = base.appendingPathComponent("VibeStudio")

            try? FileManager.default.createDirectory(
                at: appDir,
                withIntermediateDirectories: true,
                attributes: [.posixPermissions: 0o700]
            )

            return appDir
        }
    }
}

// MARK: - Errors

/// Errors thrown by ``PathConstants``.
enum PathConstantsError: LocalizedError {
    case appSupportNotFound

    var errorDescription: String? {
        switch self {
        case .appSupportNotFound:
            return "Application Support directory not found on this system"
        }
    }
}
