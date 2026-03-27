// MARK: - VibeStudio Service Models
// Все доменные модели, используемые сервисными контрактами.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Project

/// Проект — корневая сущность. Один таб = один проект.
struct Project: Codable, Identifiable, Hashable, Sendable {
    let id: UUID
    var name: String
    var path: URL
    var color: HexColor?
    var lastOpened: Date
    var shellPath: String
    var productionURL: String?

    init(
        id: UUID = UUID(),
        name: String,
        path: URL,
        color: HexColor? = nil,
        lastOpened: Date = .now,
        shellPath: String = ProcessInfo.processInfo.environment["SHELL"] ?? "/bin/zsh",
        productionURL: String? = nil
    ) {
        self.id = id
        self.name = name
        self.path = path
        self.color = color
        self.lastOpened = lastOpened
        self.shellPath = shellPath
        self.productionURL = productionURL
    }
}

/// Hex-цвет с валидацией при декодировании.
struct HexColor: Codable, Hashable, Sendable {
    let value: String

    init?(_ hex: String) {
        let cleaned = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard cleaned.count == 6,
              cleaned.allSatisfy({ $0.isHexDigit }) else {
            return nil
        }
        self.value = "#\(cleaned)"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let color = HexColor(raw) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid hex color: \(raw)"
            )
        }
        self = color
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }
}

// MARK: - Terminal

/// Activity state for a project's tab indicator dot.
///
/// Defined here (not in a SwiftUI view) so that both `TerminalService`
/// (AppKit-only) and SwiftUI views can reference it.
enum TabActivityState: Sendable {
    /// Tab is open but nothing has happened (or user already looked at it).
    case idle
    /// Output is actively flowing right now.
    case running
    /// Output appeared since user last checked — shell awaiting user reaction.
    case waitingForInput
    /// Process exited with non-zero exit code.
    case error
    /// Active tab — indicator hidden.
    case hidden
}

/// Терминальная сессия — один PTY-процесс внутри проекта.
struct TerminalSession: Identifiable, Sendable {
    let id: UUID
    let projectId: UUID
    var title: String
    var state: TerminalSessionState
    var splitDirection: SplitDirection?
    /// `true` when this session was launched by the AI agent runner (▶ button).
    ///
    /// `TerminalAreaView` uses this flag to show only the agent session when one
    /// is active, hiding regular shell sessions without killing them.  When the
    /// agent session exits the shell sessions reappear automatically.
    var isAgentSession: Bool

    init(
        id: UUID = UUID(),
        projectId: UUID,
        title: String = "zsh",
        state: TerminalSessionState = .running,
        splitDirection: SplitDirection? = nil,
        isAgentSession: Bool = false
    ) {
        self.id = id
        self.projectId = projectId
        self.title = title
        self.state = state
        self.splitDirection = splitDirection
        self.isAgentSession = isAgentSession
    }
}

enum TerminalSessionState: Sendable, Equatable {
    /// PTY-процесс запущен и активен.
    case running
    /// Есть новый вывод, но таб не активен (для индикатора).
    case hasActivity
    /// Процесс завершился с кодом возврата.
    case exited(code: Int32)
}

enum SplitDirection: String, Codable, Sendable {
    case horizontal
    case vertical
}

/// Размеры терминала в символах (для TIOCSWINSZ).
struct TerminalSize: Equatable, Sendable {
    let columns: Int
    let rows: Int
}

// MARK: - Git

struct GitStatus: Sendable, Equatable {
    let branch: String
    let aheadCount: Int
    let behindCount: Int
    let stagedFiles: [GitFile]
    let unstagedFiles: [GitFile]
    let untrackedFiles: [GitFile]

    static let empty = GitStatus(
        branch: "",
        aheadCount: 0,
        behindCount: 0,
        stagedFiles: [],
        unstagedFiles: [],
        untrackedFiles: []
    )

    /// true если рабочее дерево чистое (нет изменений, нет untracked).
    var isClean: Bool {
        stagedFiles.isEmpty && unstagedFiles.isEmpty && untrackedFiles.isEmpty
    }
}

struct GitFile: Sendable, Equatable, Hashable {
    let path: String
    let status: GitFileStatus
}

enum GitFileStatus: String, Sendable, Equatable, Hashable {
    case modified = "M"
    case added = "A"
    case deleted = "D"
    case renamed = "R"
    case copied = "C"
    case untracked = "?"
}

struct GitBranch: Sendable, Equatable, Identifiable {
    var id: String { name }
    let name: String
    let isRemote: Bool
    let isCurrent: Bool
}

struct GitDiffHunk: Sendable {
    let header: String
    let lines: [GitDiffLine]
}

struct GitDiffLine: Sendable {
    let type: DiffLineType
    let content: String
    let oldLineNumber: Int?
    let newLineNumber: Int?
}

enum DiffLineType: Sendable {
    case context
    case addition
    case deletion
}

struct GitCommitInfo: Sendable {
    let hash: String
    let shortHash: String
    let message: String
    let author: String
    let date: Date
}

// MARK: - File System

struct FileChangeEvent: Sendable {
    let path: URL
    let kind: FileChangeKind
    let timestamp: Date
}

enum FileChangeKind: Sendable {
    case created
    case modified
    case deleted
    case renamed
}

/// Узел файлового дерева (для сайдбара).
indirect enum FileTreeNode: Identifiable, Sendable {
    case file(FileEntry)
    case directory(DirectoryEntry)

    var id: String {
        switch self {
        case .file(let entry): return entry.path.path
        case .directory(let entry): return entry.path.path
        }
    }

    var name: String {
        switch self {
        case .file(let entry): return entry.path.lastPathComponent
        case .directory(let entry): return entry.path.lastPathComponent
        }
    }
}

struct FileEntry: Sendable {
    let path: URL
    var gitStatus: GitFileStatus?
}

struct DirectoryEntry: Sendable {
    let path: URL
    var children: [FileTreeNode]
    var isExpanded: Bool
}

// MARK: - Session Persistence

/// Снимок состояния приложения для восстановления при перезапуске.
struct AppSessionSnapshot: Codable, Sendable {
    let version: Int
    let capturedAt: Date
    let activeProjectId: UUID?
    let projectSessions: [ProjectSessionSnapshot]
}

struct ProjectSessionSnapshot: Codable, Sendable {
    let projectId: UUID
    let terminalLayouts: [TerminalLayoutSnapshot]
    let scrollbackFile: URL?
    let sidebarVisible: Bool
    let sidebarWidth: Double
}

struct TerminalLayoutSnapshot: Codable, Sendable {
    let sessionId: UUID
    let title: String
    let splitDirection: SplitDirection?
    let workingDirectory: URL?
}
