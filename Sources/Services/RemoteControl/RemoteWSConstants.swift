// MARK: - RemoteWSConstants
// Shared constants for Remote Control WebSocket handlers.
// macOS 14+, Swift 5.10

import Foundation
import NIOWebSocket

/// Application-defined WebSocket close codes used by Remote Control.
///
/// Codes in the 4000-4999 range are reserved by RFC 6455 §7.4.2 for
/// application-defined meanings, so we are free to assign domain-specific
/// values without colliding with the IANA registry.
///
/// Centralised here so handlers in different files (``RemoteWebSocketHandler``,
/// ``RemoteSessionBridge``) agree on a single source of truth instead of
/// duplicating magic numbers.
enum WSCloseCode {

    /// Authentication required, invalid, or timed out.
    /// Emitted by ``RemoteWebSocketHandler`` when the first WS frame is missing
    /// the auth payload, malformed, or the token is rejected.
    static let authRequired: UInt16 = 4000

    /// Heartbeat / idle timeout.
    /// Emitted by ``RemoteWebSocketHandler`` and ``RemoteSessionBridge`` when
    /// the connection has been idle longer than the configured threshold.
    static let heartbeatTimeout: UInt16 = 4004
}
