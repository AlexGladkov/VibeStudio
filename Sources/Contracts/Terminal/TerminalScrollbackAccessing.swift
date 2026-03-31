// MARK: - TerminalScrollbackAccessing Protocol
// Scrollback buffer retrieval.
// macOS 14+, Swift 5.10

import Foundation

/// Terminal scrollback buffer access.
///
/// Provides read access to a session's scrollback content,
/// typically used for persisting terminal history on app exit.
@MainActor
protocol TerminalScrollbackAccessing: AnyObject {

    /// Получить текущий scrollback-буфер сессии (для сохранения при выходе).
    ///
    /// - Parameter sessionId: ID сессии.
    /// - Returns: Текст scrollback-буфера.
    func scrollbackContent(for sessionId: UUID) -> String?
}
