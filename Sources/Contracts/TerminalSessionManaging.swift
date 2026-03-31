// MARK: - TerminalSessionManaging Protocol
// Unified terminal session management composed from focused sub-protocols (ISP).
// macOS 14+, Swift 5.10

import Foundation
import AppKit // NSView для attach

// MARK: - Protocol

/// Управление жизненным циклом терминальных сессий.
///
/// Composed from focused sub-protocols for interface segregation:
/// - ``TerminalSessionCreating`` -- create, kill, resize, split, agent launch, attach/detach
/// - ``TerminalSessionQuerying`` -- session lookup, listing, events, activity marking
/// - ``TerminalInputSending`` -- programmatic input
/// - ``TerminalScrollbackAccessing`` -- scrollback buffer retrieval
///
/// Consumers that need all terminal capabilities use `any TerminalSessionManaging`.
/// Consumers that need only a subset can depend on the narrower protocol.
///
/// Реализация ДОЛЖНА быть `@Observable` и работать на MainActor,
/// потому что UI наблюдает за списком сессий и их состояниями.
///
/// Каждая сессия -- отдельный PTY-процесс. Один проект может иметь
/// несколько сессий (split-панели).
@MainActor
protocol TerminalSessionManaging: TerminalSessionCreating, TerminalSessionQuerying, TerminalInputSending, TerminalScrollbackAccessing {}

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
