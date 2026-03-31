// MARK: - TerminalSessionCreating Protocol
// Session lifecycle: create, kill, resize, split, agent launch, attach/detach.
// macOS 14+, Swift 5.10

import Foundation
import AppKit

/// Terminal session lifecycle management.
///
/// Provides operations to create, destroy, resize, split, and attach/detach
/// PTY-backed terminal sessions. Includes agent session launching.
@MainActor
protocol TerminalSessionCreating: AnyObject {

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

    /// Launch an AI CLI agent in a dedicated PTY session.
    ///
    /// Unlike `createSession`, this does not start a shell -- it runs the
    /// agent binary directly with an allowlist-based environment.
    ///
    /// - Parameters:
    ///   - agent: The AI assistant to launch.
    ///   - projectId: ID of the project to associate the session with.
    ///   - workingDirectory: Absolute path for the agent's working directory.
    ///   - apiKeyValue: API key to inject into the agent's environment.
    /// - Returns: The created session, or `nil` if the agent could not be launched.
    @discardableResult
    func startAgentSession(
        agent: AIAssistant,
        for projectId: UUID,
        workingDirectory: String,
        apiKeyValue: String?
    ) -> TerminalSession?
}
