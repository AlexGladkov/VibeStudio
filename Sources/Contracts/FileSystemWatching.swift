// MARK: - FileSystemWatching Protocol
// FSEventStream wrapper для наблюдения за файловой системой.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Наблюдение за изменениями в директориях проектов.
///
/// Обёртка над FSEventStream (CoreServices). Реализация НЕ является
/// @Observable -- это infrastructure-сервис, который поставляет события
/// через AsyncStream. Потребители (ViewModel) подписываются и обновляют
/// своё @Observable состояние.
///
/// Потокобезопасность: все методы можно вызывать с любого потока.
/// FSEventStream управляется внутри на выделенной DispatchQueue.
protocol FileSystemWatching: Sendable {

    // MARK: - Watch Management

    /// Начать наблюдение за директорией.
    ///
    /// FSEventStream создаётся с debounce-интервалом (по умолчанию 0.3с)
    /// для предотвращения шторма событий при массовых операциях (npm install, git checkout).
    ///
    /// Пути из .gitignore автоматически фильтруются (если gitignore доступен).
    ///
    /// - Parameters:
    ///   - directory: Директория для наблюдения (рекурсивно).
    ///   - options: Настройки наблюдения.
    /// - Returns: ID наблюдателя (для остановки).
    /// - Throws: `FileSystemWatcherError.pathNotFound`,
    ///           `FileSystemWatcherError.alreadyWatching`,
    ///           `FileSystemWatcherError.streamCreationFailed`.
    @discardableResult
    func watch(
        directory: URL,
        options: WatchOptions
    ) throws -> WatchToken

    /// Остановить конкретное наблюдение.
    func unwatch(_ token: WatchToken)

    /// Остановить все наблюдения (при выходе из приложения).
    func unwatchAll()

    // MARK: - Events

    /// Поток событий об изменениях файлов.
    ///
    /// Debounced и отфильтрованный от игнорируемых путей.
    /// Consumers получают только релевантные изменения.
    var events: AsyncStream<FileChangeEvent> { get }

    // MARK: - State

    /// Список текущих активных наблюдений.
    var activeWatches: [WatchInfo] { get }
}

// MARK: - Default parameters

extension FileSystemWatching {
    @discardableResult
    func watch(
        directory: URL,
        options: WatchOptions = .default
    ) throws -> WatchToken {
        try watch(directory: directory, options: options)
    }
}

// MARK: - Supporting Types

/// Токен для отмены наблюдения. Непрозрачный идентификатор.
struct WatchToken: Hashable, Sendable {
    let id: UUID

    init() {
        self.id = UUID()
    }
}

/// Настройки наблюдения за директорией.
struct WatchOptions: Sendable {
    /// Интервал debounce для FSEventStream (секунды).
    let debounceInterval: TimeInterval

    /// Паттерны для игнорирования (glob-синтаксис).
    /// По умолчанию: node_modules, .git, .build, DerivedData, .DS_Store
    let ignoredPatterns: [String]

    /// Загружать .gitignore и добавлять к ignoredPatterns.
    let respectGitignore: Bool

    /// Максимальная глубина рекурсии (nil = без ограничений).
    let maxDepth: Int?

    static let `default` = WatchOptions(
        debounceInterval: 0.3,
        ignoredPatterns: [
            "node_modules",
            ".git",
            ".build",
            "DerivedData",
            ".DS_Store",
            "*.swp",
            "*~"
        ],
        respectGitignore: true,
        maxDepth: nil
    )
}

/// Информация об активном наблюдении (для отладки/UI).
struct WatchInfo: Sendable {
    let token: WatchToken
    let directory: URL
    let options: WatchOptions
    let startedAt: Date
}
