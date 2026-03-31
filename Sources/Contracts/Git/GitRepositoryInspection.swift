// MARK: - GitRepositoryInspection Protocol
// Repository validation, root discovery and initialization.
// macOS 14+, Swift 5.10

import Foundation

/// Git repository discovery and initialization.
///
/// Provides utilities to check whether a path is inside a repository,
/// find the repository root, and initialize new repositories.
protocol GitRepositoryInspection: Sendable {

    /// Проверить, является ли путь git-репозиторием.
    func isRepository(at path: URL) async -> Bool

    /// Получить корень репозитория для любого пути внутри него.
    func repositoryRoot(for path: URL) async throws -> URL

    /// Инициализировать git-репозиторий (git init).
    func initRepository(at path: URL) async throws
}
