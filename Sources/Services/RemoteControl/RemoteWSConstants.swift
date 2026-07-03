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

/// Payload size limits enforced by Remote Control transport handlers.
///
/// Centralised so HTTP and WebSocket handlers share a single source of truth
/// instead of duplicating magic byte counts.
enum RemoteLimits {

    /// Maximum accepted HTTP request body size (64 KB) for API requests.
    static let maxRequestBodyBytes = 65_536

    /// Maximum accepted PTY input payload size (4 KB) per WebSocket message.
    static let maxPTYInputBytes = 4096
}

/// Timeout durations (in seconds) enforced by Remote Control WebSocket handlers.
///
/// Centralised so ``RemoteWebSocketHandler`` does not embed magic literals in
/// `Task.sleep(for:)` calls.
enum RemoteWSTimeouts {

    /// Grace period for the client to send the auth frame before the
    /// unauthenticated connection is closed.
    static let authTimeoutSeconds = 10

    /// Idle heartbeat interval; the connection is closed if no activity resets
    /// the timer within this window.
    static let heartbeatIntervalSeconds = 60
}
