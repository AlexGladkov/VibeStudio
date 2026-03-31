// MARK: - GitRemoteOperating Protocol
// Remote operations: push, pull, fetch, remote management.
// macOS 14+, Swift 5.10

import Foundation

/// Git remote operations.
///
/// Provides network-facing git capabilities: push, pull, fetch,
/// and remote configuration management.
protocol GitRemoteOperating: Sendable {

    /// Push текущую ветку на remote.
    ///
    /// - Parameters:
    ///   - remote: Имя remote (по умолчанию "origin").
    ///   - repository: Корневой путь репозитория.
    /// - Throws: `GitServiceError.pushRejected` если remote отклонил.
    func push(remote: String, at repository: URL) async throws

    /// Pull с remote (fetch + merge).
    ///
    /// - Parameters:
    ///   - remote: Имя remote (по умолчанию "origin").
    ///   - repository: Корневой путь репозитория.
    /// - Throws: `GitServiceError.mergeConflict` при конфликтах.
    func pull(remote: String, at repository: URL) async throws

    /// Fetch с remote (только скачать, без merge).
    func fetch(remote: String, at repository: URL) async throws

    /// Push конкретной ветки на remote (git push <remote> <branch>).
    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws

    /// Pull конкретной ветки:
    /// -- текущая ветка: git pull <remote> (уважает merge/rebase конфиг)
    /// -- не текущая:   git fetch <remote> <branch>:<branch> (fast-forward, без switch)
    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws

    /// Добавить remote (git remote add <name> <url>).
    func addRemote(name: String, url: String, at repository: URL) async throws

    /// Получить URL remote по имени. Возвращает nil если remote не настроен.
    ///
    /// Выполняет `git remote get-url <name>`.
    ///
    /// - Parameters:
    ///   - name: Имя remote (например "origin").
    ///   - repository: Корневой путь репозитория.
    /// - Returns: URL remote или nil если remote не настроен.
    func remoteURL(name: String, at repository: URL) async -> String?

    /// Вернуть сконфигурированный remote для ветки (из git config), или первый remote репо, или "origin".
    func defaultRemote(for branch: String?, at repository: URL) async -> String
}
