// MARK: - TerminalSessionManaging Protocol
// Управление PTY-сессиями через SwiftTerm.
// macOS 14+, Swift 5.10

import Foundation
import AppKit // NSView для attach

// MARK: - Protocol

/// Управление жизненным циклом терминальных сессий.
///
/// Реализация ДОЛЖНА быть `@Observable` и работать на MainActor,
/// потому что UI наблюдает за списком сессий и их состояниями.
///
/// Каждая сессия -- отдельный PTY-процесс. Один проект может иметь
/// несколько сессий (split-панели).
@MainActor
protocol TerminalSessionManaging: AnyObject, Observable {

    // MARK: - Observable State

    /// Все активные сессии, сгруппированные по проекту.
    /// Ключ -- projectId, значение -- массив сессий в порядке создания.
    var sessionsByProject: [UUID: [TerminalSession]] { get }

    /// Агрегированное состояние активности для каждого проекта.
    ///
    /// Обновляется автоматически при:
    /// - выводе в терминале (`.running`)
    /// - завершении процесса с exitCode == 0 (`.idle`)
    /// - завершении процесса с exitCode != 0 (`.error`)
    /// - удалении всех сессий проекта (`.idle`)
    ///
    /// UI-views читают это свойство через `@Observable` — мультикаст
    /// гарантирован, в отличие от `sessionEvents` (unicast `AsyncStream`).
    var projectActivityStates: [UUID: TabActivityState] { get }

    // MARK: - Lifecycle

    /// Создать новую терминальную сессию для проекта.
    ///
    /// PTY-процесс запускается немедленно, shell `cd`-ится в директорию проекта.
    ///
    /// - Parameters:
    ///   - projectId: ID проекта.
    ///   - shell: Путь к shell-бинарнику (по умолчанию из Project.shellPath).
    ///   - workingDirectory: Рабочая директория (по умолчанию Project.path).
    ///   - size: Начальные размеры терминала в символах.
    /// - Returns: Созданная сессия.
    /// - Throws: `TerminalSessionError.projectNotFound`,
    ///           `TerminalSessionError.shellNotFound`,
    ///           `TerminalSessionError.ptyCreationFailed`,
    ///           `TerminalSessionError.sessionLimitReached`.
    @discardableResult
    func createSession(
        for projectId: UUID,
        shell: String?,
        workingDirectory: URL?,
        size: TerminalSize
    ) throws -> TerminalSession

    /// Присоединить NSView к терминальной сессии для отображения.
    ///
    /// Вызывается из NSViewRepresentable.makeNSView. Возвращает SwiftTerm view,
    /// который нужно добавить как subview.
    ///
    /// - Parameter sessionId: ID сессии.
    /// - Returns: NSView терминала (LocalProcessTerminalView).
    /// - Throws: `TerminalSessionError.sessionNotFound`.
    func attachView(to sessionId: UUID) throws -> NSView

    /// Отсоединить view от сессии (при переключении таба).
    /// PTY-процесс продолжает работать в фоне.
    ///
    /// - Parameter sessionId: ID сессии.
    func detachView(from sessionId: UUID)

    /// Изменить размер PTY (вызывается при resize окна/панели).
    /// Посылает TIOCSWINSZ в PTY.
    ///
    /// - Parameters:
    ///   - sessionId: ID сессии.
    ///   - size: Новые размеры.
    func resize(session sessionId: UUID, to size: TerminalSize)

    /// Завершить сессию. Посылает SIGHUP процессу.
    ///
    /// - Parameter sessionId: ID сессии.
    /// - Parameter force: true = SIGKILL, false = SIGHUP (по умолчанию).
    func killSession(_ sessionId: UUID, force: Bool)

    /// Завершить все сессии проекта (при закрытии таба).
    func killAllSessions(for projectId: UUID)

    // MARK: - Split Panels

    /// Создать split рядом с существующей сессией.
    ///
    /// - Parameters:
    ///   - sessionId: ID сессии, рядом с которой создать split.
    ///   - direction: Направление split.
    ///   - size: Начальные размеры новой панели.
    /// - Returns: Новая терминальная сессия.
    @discardableResult
    func split(
        _ sessionId: UUID,
        direction: SplitDirection,
        size: TerminalSize
    ) throws -> TerminalSession

    // MARK: - Query

    /// Получить сессию по ID.
    func session(for id: UUID) -> TerminalSession?

    /// Все сессии конкретного проекта.
    func sessions(for projectId: UUID) -> [TerminalSession]

    // MARK: - Activity Tracking

    /// Подписка на события активности сессий (для индикаторов на табах).
    /// Реализация уведомляет через AsyncStream, когда в сессии появляется
    /// новый вывод, процесс завершается, и т.д.
    var sessionEvents: AsyncStream<TerminalSessionEvent> { get }

    // MARK: - Scrollback

    /// Получить текущий scrollback-буфер сессии (для сохранения при выходе).
    ///
    /// - Parameter sessionId: ID сессии.
    /// - Returns: Текст scrollback-буфера.
    func scrollbackContent(for sessionId: UUID) -> String?

    // MARK: - Input

    /// Send text input to a terminal session (as if the user typed it).
    ///
    /// Use this to programmatically send commands to the running shell.
    /// The text is written directly to the PTY's stdin.
    ///
    /// - Parameters:
    ///   - text: The text to send, including any newline for command execution.
    ///   - sessionId: Target session ID.
    func sendInput(_ text: String, to sessionId: UUID)
}

// MARK: - Default parameters

extension TerminalSessionManaging {
    @discardableResult
    func createSession(
        for projectId: UUID,
        shell: String? = nil,
        workingDirectory: URL? = nil,
        size: TerminalSize = TerminalSize(columns: 80, rows: 24)
    ) throws -> TerminalSession {
        try createSession(
            for: projectId,
            shell: shell,
            workingDirectory: workingDirectory,
            size: size
        )
    }

    func killSession(_ sessionId: UUID) {
        killSession(sessionId, force: false)
    }
}

// MARK: - Terminal Session Events

/// События жизненного цикла терминальных сессий.
enum TerminalSessionEvent: Sendable {
    /// Новый вывод в неактивной сессии (для индикатора на табе).
    case activityDetected(sessionId: UUID, projectId: UUID)

    /// Процесс в сессии завершился.
    case processExited(sessionId: UUID, projectId: UUID, exitCode: Int32)

    /// Заголовок сессии изменился (через xterm escape sequence).
    case titleChanged(sessionId: UUID, newTitle: String)

    /// Bell в терминале.
    case bell(sessionId: UUID)
}
