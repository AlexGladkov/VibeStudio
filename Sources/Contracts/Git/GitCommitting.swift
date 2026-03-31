// MARK: - GitCommitting Protocol
// Commit creation.
// macOS 14+, Swift 5.10

import Foundation

/// Git commit creation capability.
///
/// Provides the ability to create commits from the current staging area.
protocol GitCommitting: Sendable {

    /// Создать коммит.
    ///
    /// - Parameters:
    ///   - message: Сообщение коммита. Не может быть пустым.
    ///   - repository: Корневой путь репозитория.
    /// - Returns: Hash созданного коммита.
    @discardableResult
    func commit(message: String, at repository: URL) async throws -> String
}
