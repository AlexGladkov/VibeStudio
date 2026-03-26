// MARK: - SessionPersisting Protocol
// Сохранение/восстановление состояния приложения при перезапуске.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Protocol

/// Сохранение и восстановление состояния приложения.
///
/// Stateless сервис: читает/пишет на диск по запросу.
/// НЕ является @Observable -- вызывается из AppDelegate/AppLifecycle.
///
/// Хранение:
/// - Snapshot: ~/Library/Application Support/VibeStudio/session.json
/// - Scrollback: ~/Library/Application Support/VibeStudio/scrollback/<sessionId>.txt
protocol SessionPersisting: Sendable {

    // MARK: - Snapshot

    /// Сохранить полный snapshot текущего состояния.
    ///
    /// Вызывается:
    /// - При applicationWillTerminate
    /// - Периодически (auto-save каждые 30 секунд)
    /// - При явном вызове (Cmd+S если нужно)
    ///
    /// - Parameter snapshot: Снимок состояния приложения.
    /// - Throws: `SessionPersistenceError.encodingFailed`.
    func save(snapshot: AppSessionSnapshot) async throws

    /// Восстановить snapshot при запуске.
    ///
    /// - Returns: Последний сохранённый snapshot, или nil если нет/повреждён.
    /// - Throws: `SessionPersistenceError.decodingFailed`,
    ///           `SessionPersistenceError.incompatibleVersion`.
    func restore() async throws -> AppSessionSnapshot?

    /// Удалить сохранённый snapshot (сброс состояния).
    func clear() async throws

    // MARK: - Scrollback Persistence

    /// Сохранить scrollback-буфер сессии в файл.
    ///
    /// Каждая терминальная сессия имеет свой файл scrollback.
    /// При перезапуске приложения scrollback загружается и отображается
    /// в новой PTY-сессии (имитация продолжения).
    ///
    /// - Parameters:
    ///   - content: Текст scrollback-буфера.
    ///   - sessionId: ID терминальной сессии.
    /// - Throws: `SessionPersistenceError.scrollbackWriteFailed`.
    func saveScrollback(_ content: String, for sessionId: UUID) async throws

    /// Загрузить scrollback-буфер сессии.
    ///
    /// - Parameter sessionId: ID терминальной сессии.
    /// - Returns: Текст scrollback-буфера, или nil если нет файла.
    func loadScrollback(for sessionId: UUID) async -> String?

    /// Удалить scrollback-файл сессии (при явном удалении проекта).
    func deleteScrollback(for sessionId: UUID) async throws

    /// Удалить все scrollback-файлы, не привязанные к активным сессиям.
    /// Вызывается при запуске для cleanup осиротевших файлов.
    ///
    /// - Parameter activeSessionIds: ID сессий, которые ещё актуальны.
    /// - Returns: Количество удалённых файлов.
    @discardableResult
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int

    // MARK: - Meta

    /// Путь к директории хранения.
    var storageDirectory: URL { get }

    /// Текущая версия формата snapshot.
    var currentSnapshotVersion: Int { get }
}
