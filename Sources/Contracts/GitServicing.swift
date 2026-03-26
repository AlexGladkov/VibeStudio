// MARK: - GitServicing Protocol
// Асинхронные git-операции через subprocess.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Git-операции через CLI subprocess.
///
/// Все методы async -- git-команды выполняются в фоновом потоке.
/// Реализация НЕ является @Observable. Вместо этого GitService --
/// stateless утилитарный сервис. Observable-состояние (GitStatus)
/// хранится и обновляется вызывающим кодом (например, ViewModel).
///
/// Причина: git-операции привязаны к конкретному пути, а не к
/// глобальному состоянию. Один GitService обслуживает все проекты.
protocol GitServicing: Sendable {

    // MARK: - Status & Info

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

    /// Список веток (локальных и remote).
    func branches(at repository: URL) async throws -> [GitBranch]

    /// Лог коммитов (последние N).
    ///
    /// - Parameters:
    ///   - limit: Максимальное количество коммитов.
    ///   - repository: Корневой путь репозитория.
    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo]

    // MARK: - Staging

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

    // MARK: - Commit

    /// Создать коммит.
    ///
    /// - Parameters:
    ///   - message: Сообщение коммита. Не может быть пустым.
    ///   - repository: Корневой путь репозитория.
    /// - Returns: Hash созданного коммита.
    @discardableResult
    func commit(message: String, at repository: URL) async throws -> String

    // MARK: - Remote

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
    /// — текущая ветка: git pull <remote> (уважает merge/rebase конфиг)
    /// — не текущая:   git fetch <remote> <branch>:<branch> (fast-forward, без switch)
    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws

    // MARK: - Branches

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

    // MARK: - Utility

    /// Проверить, является ли путь git-репозиторием.
    func isRepository(at path: URL) async -> Bool

    /// Получить корень репозитория для любого пути внутри него.
    func repositoryRoot(for path: URL) async throws -> URL

    /// Инициализировать git-репозиторий (git init).
    func initRepository(at path: URL) async throws

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

    // MARK: - Ahead/Behind

    /// Get ahead/behind count relative to upstream.
    ///
    /// - Parameter repository: Repository root path.
    /// - Returns: Tuple of (ahead, behind) counts.
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int)
}

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
