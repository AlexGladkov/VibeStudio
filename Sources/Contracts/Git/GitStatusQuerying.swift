// MARK: - GitStatusQuerying Protocol
// Status, diff, log and ahead/behind inspection.
// macOS 14+, Swift 5.10

import Foundation

/// Git status inspection capabilities.
///
/// Provides read-only queries for repository state: working tree status,
/// file diffs, commit history, and upstream divergence counts.
protocol GitStatusQuerying: Sendable {

    /// Получить полный статус рабочего дерева.
    ///
    /// Выполняет `git status --porcelain=v2 --branch`.
    ///
    /// - Parameter repository: Корневой путь git-репозитория.
    /// - Returns: Текущий статус.
    /// - Throws: `GitServiceError.notARepository`,
    ///           `GitServiceError.commandFailed`.
    func status(at repository: URL) async throws -> GitStatus

    /// Показать diff для конкретного файла.
    ///
    /// - Parameters:
    ///   - file: Относительный путь файла (от корня репозитория).
    ///   - staged: true для staged diff (--cached), false для unstaged.
    ///   - repository: Корневой путь репозитория.
    /// - Returns: Массив diff-ханков.
    func diff(
        file: String,
        staged: Bool,
        at repository: URL
    ) async throws -> [GitDiffHunk]

    /// Полный staged diff как сырая строка (git diff --staged).
    ///
    /// Используется для генерации сообщения коммита через AI.
    ///
    /// - Parameter repository: Корневой путь репозитория.
    /// - Returns: Сырой вывод git diff --staged.
    func fullStagedDiff(at repository: URL) async throws -> String

    /// Все незакоммиченные изменения относительно HEAD (staged + unstaged).
    ///
    /// Используется для AI-генерации сообщения коммита.
    /// - Returns: Сырой вывод git diff HEAD.
    func headDiff(at repository: URL) async throws -> String

    /// Лог коммитов (последние N).
    ///
    /// - Parameters:
    ///   - limit: Максимальное количество коммитов.
    ///   - repository: Корневой путь репозитория.
    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo]

    /// Get ahead/behind count relative to upstream.
    ///
    /// - Parameter repository: Repository root path.
    /// - Returns: Tuple of (ahead, behind) counts.
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int)

    /// Per-file line addition/deletion counts for the working tree.
    ///
    /// Runs `git diff --numstat` (unstaged) and `git diff --cached --numstat` (staged)
    /// and merges the results. Files not in the diff return nil in the dictionary.
    ///
    /// - Parameter repository: Repository root path.
    /// - Returns: Dictionary keyed by relative file path.
    func diffStats(at repository: URL) async throws -> [String: GitDiffStat]
}
