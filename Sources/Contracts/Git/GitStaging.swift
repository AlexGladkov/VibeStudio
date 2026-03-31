// MARK: - GitStaging Protocol
// Stage and unstage operations.
// macOS 14+, Swift 5.10

import Foundation

/// Git staging area manipulation.
///
/// Provides operations to add files to and remove files from
/// the Git index (staging area).
protocol GitStaging: Sendable {

    /// Stage файлы (git add).
    ///
    /// - Parameters:
    ///   - files: Относительные пути файлов. Пустой массив = stage all.
    ///   - repository: Корневой путь репозитория.
    func stage(files: [String], at repository: URL) async throws

    /// Unstage файлы (git restore --staged).
    ///
    /// - Parameters:
    ///   - files: Относительные пути файлов. Пустой массив = unstage all.
    ///   - repository: Корневой путь репозитория.
    func unstage(files: [String], at repository: URL) async throws
}
