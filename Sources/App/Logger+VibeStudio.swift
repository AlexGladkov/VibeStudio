// MARK: - Logger Extensions
// Shared OSLog logger instances for structured logging.
// macOS 14+, Swift 5.10

import OSLog

extension Logger {

    /// Subsystem identifier derived from the app's bundle ID.
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.vibestudio.app"

    // MARK: - Category Loggers

    /// Application lifecycle, launch, termination, session management.
    static let app = Logger(subsystem: subsystem, category: "App")

    /// Terminal PTY lifecycle, session creation, view attach/detach.
    static let terminal = Logger(subsystem: subsystem, category: "Terminal")

    /// Git operations: status, branches, checkout, commit, push/pull.
    static let git = Logger(subsystem: subsystem, category: "Git")

    /// File tree scanning, file system watcher events.
    static let fileTree = Logger(subsystem: subsystem, category: "FileTree")

    /// Session persistence: save, restore, scrollback.
    static let session = Logger(subsystem: subsystem, category: "Session")

    /// UI-layer events: sidebar, toolbar, tabs, welcome screen.
    static let ui = Logger(subsystem: subsystem, category: "UI")

    /// Project persistence: save, load, migration.
    static let persistence = Logger(subsystem: subsystem, category: "Persistence")
}
