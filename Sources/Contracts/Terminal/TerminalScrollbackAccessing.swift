// MARK: - TerminalScrollbackAccessing Protocol
// Scrollback buffer retrieval.
// macOS 14+, Swift 5.10

import Foundation

/// Terminal scrollback buffer access.
///
/// Provides read access to a session's raw scrollback content for remote
/// delivery. Use ``TerminalService.rawScrollbackContent(for:)`` — it works
/// independently of the view's window hierarchy and is the only production
/// call site.
///
/// P2-4: The previous `scrollbackContent(for:)` method (with its
/// `guard view.window != nil` guard) has been removed from this protocol.
/// Remote Control never used it — the production call site in
/// `RemoteAPIHandlers+Sessions` calls `rawScrollbackContent(for:)` directly
/// on `TerminalService`. The window-gated variant returned `nil` for every
/// remote request (terminal views have no window when served over the network)
/// and was therefore a dead API.
@MainActor
protocol TerminalScrollbackAccessing: AnyObject {

    /// Получить сырой scrollback-буфер сессии без проверки window-иерархии.
    ///
    /// - Parameter sessionId: ID сессии.
    /// - Returns: Текст scrollback-буфера, или `nil` если сессия не найдена.
    func rawScrollbackContent(for sessionId: UUID) -> String?
}
