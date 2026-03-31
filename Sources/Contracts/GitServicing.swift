// MARK: - GitServicing Protocol
// Unified git operations composed from focused sub-protocols (ISP).
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Unified Git operations -- all capabilities in one type.
///
/// Composed from focused sub-protocols for interface segregation:
/// - ``GitStatusQuerying`` -- status, diff, log, ahead/behind
/// - ``GitStaging`` -- stage/unstage
/// - ``GitCommitting`` -- commit
/// - ``GitRemoteOperating`` -- push/pull/fetch/remote management
/// - ``GitBranching`` -- branches, checkout, create
/// - ``GitRepositoryInspection`` -- init, validate, root
///
/// Consumers that need all git capabilities use `any GitServicing`.
/// Consumers that need only a subset can depend on the narrower protocol.
///
/// Все методы async -- git-команды выполняются в фоновом потоке.
/// Реализация НЕ является @Observable. Вместо этого GitService --
/// stateless утилитарный сервис. Observable-состояние (GitStatus)
/// хранится и обновляется вызывающим кодом (например, ViewModel).
///
/// Причина: git-операции привязаны к конкретному пути, а не к
/// глобальному состоянию. Один GitService обслуживает все проекты.
protocol GitServicing: GitStatusQuerying, GitStaging, GitCommitting, GitRemoteOperating, GitBranching, GitRepositoryInspection {}

// MARK: - Default parameters

extension GitServicing {
    func push(at repository: URL) async throws {
        try await push(remote: "origin", at: repository)
    }

    func pull(at repository: URL) async throws {
        try await pull(remote: "origin", at: repository)
    }

    func fetch(at repository: URL) async throws {
        try await fetch(remote: "origin", at: repository)
    }

    func pushBranch(_ branch: String, at repository: URL) async throws {
        try await pushBranch(branch, remote: "origin", at: repository)
    }

    func pullBranch(_ branch: String, isCurrent: Bool, at repository: URL) async throws {
        try await pullBranch(branch, isCurrent: isCurrent, remote: "origin", at: repository)
    }

    func createBranch(
        name: String,
        at repository: URL
    ) async throws {
        try await createBranch(name: name, from: nil, at: repository)
    }

    func log(at repository: URL) async throws -> [GitCommitInfo] {
        try await log(limit: 50, at: repository)
    }

    func remoteURL(at repository: URL) async -> String? {
        await remoteURL(name: "origin", at: repository)
    }
}
