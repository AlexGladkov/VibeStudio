// MARK: - VibeStudio Service Errors
// Кастомные ошибки для каждого сервисного контракта.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - ProjectManagerError

enum ProjectManagerError: LocalizedError, Sendable {
    /// Путь не существует или не является директорией.
    case invalidPath(URL)
    /// Проект с таким путём уже добавлен.
    case duplicate(existingId: UUID, path: URL)
    /// Проект с указанным ID не найден.
    case notFound(UUID)
    /// Ошибка чтения/записи файла projects.json.
    case persistenceFailed(underlying: Error)
    /// Превышен лимит проектов (защита от OOM).
    case projectLimitReached(max: Int)

    var errorDescription: String? {
        switch self {
        case .invalidPath(let url):
            return "Path does not exist or is not a directory: \(url.path)"
        case .duplicate(_, let path):
            return "Project already exists at: \(path.path)"
        case .notFound(let id):
            return "Project not found: \(id)"
        case .persistenceFailed(let error):
            return "Failed to persist project list: \(error.localizedDescription)"
        case .projectLimitReached(let max):
            return "Maximum number of projects reached: \(max)"
        }
    }
}

// MARK: - TerminalSessionError

enum TerminalSessionError: LocalizedError, Sendable {
    /// Не удалось создать PTY.
    case ptyCreationFailed(reason: String)
    /// Сессия не найдена.
    case sessionNotFound(UUID)
    /// Проект не найден (для создания сессии в контексте проекта).
    case projectNotFound(UUID)
    /// Попытка операции над завершённой сессией.
    case sessionAlreadyExited(sessionId: UUID, exitCode: Int32)
    /// Превышен лимит сессий на проект (защита от fork bomb).
    case sessionLimitReached(projectId: UUID, max: Int)
    /// Shell-бинарник не найден.
    case shellNotFound(path: String)

    var errorDescription: String? {
        switch self {
        case .ptyCreationFailed(let reason):
            return "PTY creation failed: \(reason)"
        case .sessionNotFound(let id):
            return "Terminal session not found: \(id)"
        case .projectNotFound(let id):
            return "Project not found for terminal session: \(id)"
        case .sessionAlreadyExited(let id, let code):
            return "Session \(id) already exited with code \(code)"
        case .sessionLimitReached(let projectId, let max):
            return "Session limit (\(max)) reached for project \(projectId)"
        case .shellNotFound(let path):
            return "Shell not found at: \(path)"
        }
    }
}

// MARK: - GitServiceError

enum GitServiceError: LocalizedError, Sendable {
    /// git не установлен или не найден в PATH.
    case gitNotFound
    /// Директория не является git-репозиторием.
    case notARepository(path: URL)
    /// git-команда завершилась с ненулевым кодом.
    case commandFailed(command: String, exitCode: Int32, stderr: String)
    /// Таймаут выполнения git-команды.
    case timeout(command: String, seconds: TimeInterval)
    /// Конфликт при merge/rebase.
    case mergeConflict(files: [String])
    /// Push отклонён (нужен pull).
    case pushRejected(reason: String)
    /// Парсинг вывода git не удался.
    case parseError(command: String, output: String)

    var errorDescription: String? {
        switch self {
        case .gitNotFound:
            return "git executable not found. Install Xcode Command Line Tools."
        case .notARepository(let path):
            return "Not a git repository: \(path.path)"
        case .commandFailed(let cmd, let code, let stderr):
            return "git \(cmd) failed (exit \(code)): \(stderr)"
        case .timeout(let cmd, let seconds):
            return "git \(cmd) timed out after \(Int(seconds))s"
        case .mergeConflict(let files):
            return "Merge conflict in \(files.count) file(s): \(files.joined(separator: ", "))"
        case .pushRejected(let reason):
            return "Push rejected: \(reason)"
        case .parseError(let cmd, _):
            return "Failed to parse output of git \(cmd)"
        }
    }
}

// MARK: - AICommitServiceError

/// Errors produced by ``AICommitService``.
enum AICommitServiceError: LocalizedError, Sendable {
    /// `ANTHROPIC_API_KEY` environment variable is not set or empty.
    case missingAPIKey
    /// The Anthropic API returned a non-200 HTTP status.
    case apiError(statusCode: Int)
    /// The API response body could not be parsed.
    case invalidResponseFormat

    var errorDescription: String? {
        switch self {
        case .missingAPIKey:
            return "ANTHROPIC_API_KEY not set in environment"
        case .apiError(let statusCode):
            return "Anthropic API returned status \(statusCode)"
        case .invalidResponseFormat:
            return "Invalid API response format"
        }
    }
}

// MARK: - FileSystemWatcherError

enum FileSystemWatcherError: LocalizedError, Sendable {
    /// Не удалось создать FSEventStream.
    case streamCreationFailed(path: URL)
    /// Путь не существует.
    case pathNotFound(URL)
    /// Watcher уже запущен для этого пути.
    case alreadyWatching(path: URL)

    var errorDescription: String? {
        switch self {
        case .streamCreationFailed(let url):
            return "Failed to create FSEventStream for: \(url.path)"
        case .pathNotFound(let url):
            return "Watch path does not exist: \(url.path)"
        case .alreadyWatching(let url):
            return "Already watching: \(url.path)"
        }
    }
}

// MARK: - SessionPersistenceError

enum SessionPersistenceError: LocalizedError, Sendable {
    /// Не удалось сериализовать/десериализовать snapshot.
    case encodingFailed(underlying: Error)
    case decodingFailed(underlying: Error)
    /// Файл snapshot повреждён или отсутствует.
    case snapshotCorrupted(path: URL)
    /// Несовместимая версия snapshot (после мажорного обновления приложения).
    case incompatibleVersion(found: Int, expected: Int)
    /// Ошибка записи scrollback на диск.
    case scrollbackWriteFailed(sessionId: UUID, underlying: Error)

    var errorDescription: String? {
        switch self {
        case .encodingFailed(let error):
            return "Session encoding failed: \(error.localizedDescription)"
        case .decodingFailed(let error):
            return "Session decoding failed: \(error.localizedDescription)"
        case .snapshotCorrupted(let url):
            return "Session snapshot corrupted: \(url.path)"
        case .incompatibleVersion(let found, let expected):
            return "Snapshot version \(found) is incompatible (expected \(expected))"
        case .scrollbackWriteFailed(let id, let error):
            return "Scrollback write failed for session \(id): \(error.localizedDescription)"
        }
    }
}
