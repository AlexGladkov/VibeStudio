// MARK: - GitBranching Protocol
// Branch listing, checkout and creation.
// macOS 14+, Swift 5.10

import Foundation

/// Git branch management capabilities.
///
/// Provides operations for listing, switching, and creating branches.
protocol GitBranching: Sendable {

    /// Список веток (локальных и remote).
    func branches(at repository: URL) async throws -> [GitBranch]

    /// Переключить ветку (git checkout / git switch).
    ///
    /// - Parameters:
    ///   - branch: Имя ветки.
    ///   - repository: Корневой путь репозитория.
    func checkout(branch: String, at repository: URL) async throws

    /// Создать и переключиться на новую ветку.
    ///
    /// - Parameters:
    ///   - name: Имя новой ветки.
    ///   - startPoint: Стартовая точка (по умолчанию HEAD).
    ///   - repository: Корневой путь репозитория.
    func createBranch(
        name: String,
        from startPoint: String?,
        at repository: URL
    ) async throws
}
