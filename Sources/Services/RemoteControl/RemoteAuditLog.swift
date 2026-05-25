// MARK: - RemoteAuditLog
// Structured audit logging for all Remote Control security-relevant events.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Centralised audit logging for the Remote Control server.
///
/// Every security-relevant event (authentication, connection, terminal input)
/// is logged through this enum so that audit trails are consistent and
/// discoverable in Console.app under the `RemoteControl` category.
///
/// Log levels:
/// - `.info` -- successful operations (connect, disconnect, input).
/// - `.error` -- failed authentication, lockout events.
/// - `.fault` -- server disabled due to security policy violation.
enum RemoteAuditLog {

    // MARK: - Authentication

    /// Log an authentication attempt (PIN validation).
    ///
    /// - Parameters:
    ///   - ip: The client's IP address.
    ///   - success: Whether the PIN was accepted.
    static func authAttempt(ip: String, success: Bool) {
        if success {
            Logger.remoteControl.info("[AUDIT] Auth success from IP: \(ip, privacy: .public)")
        } else {
            Logger.remoteControl.error("[AUDIT] Auth failure from IP: \(ip, privacy: .public)")
        }
    }

    /// Log a rate-limit lockout event.
    ///
    /// - Parameters:
    ///   - ip: The locked-out IP address.
    ///   - global: Whether this triggered a global (all-IP) lockout.
    static func authLockout(ip: String, global: Bool) {
        if global {
            Logger.remoteControl.fault("[AUDIT] GLOBAL LOCKOUT triggered by IP: \(ip, privacy: .public)")
        } else {
            Logger.remoteControl.error("[AUDIT] IP lockout: \(ip, privacy: .public)")
        }
    }

    // MARK: - Device Lifecycle

    /// Log a successful device connection.
    ///
    /// - Parameters:
    ///   - device: The authenticated remote device.
    ///   - sessionId: The terminal session the device attached to.
    static func deviceConnect(device: RemoteDevice, sessionId: UUID) {
        Logger.remoteControl.info(
            "[AUDIT] Device connected: \(device.displayName, privacy: .public) (\(device.ipAddress, privacy: .public)) -> session \(sessionId)"
        )
    }

    /// Log a device disconnection.
    ///
    /// - Parameters:
    ///   - deviceId: The disconnected device's identifier.
    ///   - reason: Human-readable reason (e.g. "idle_timeout", "kicked_by_host").
    static func deviceDisconnect(deviceId: UUID, reason: String) {
        Logger.remoteControl.info(
            "[AUDIT] Device disconnected: \(deviceId) reason=\(reason, privacy: .public)"
        )
    }

    // MARK: - Terminal I/O

    /// Log terminal input relayed from a remote device.
    ///
    /// Only the byte length is logged -- **never** the content itself, which
    /// could contain passwords, API keys, or other secrets.
    ///
    /// - Parameters:
    ///   - deviceId: The device that sent the input.
    ///   - sessionId: The terminal session that received the input.
    ///   - length: Number of UTF-8 bytes in the input payload.
    static func terminalInput(deviceId: UUID, sessionId: UUID, length: Int) {
        Logger.remoteControl.info(
            "[AUDIT] Input: device=\(deviceId) session=\(sessionId) bytes=\(length)"
        )
    }

    // MARK: - Server Lifecycle

    /// Log the server being disabled due to a security policy violation.
    ///
    /// - Parameter reason: Description of the security event.
    static func serverDisabled(reason: String) {
        Logger.remoteControl.fault(
            "[AUDIT] Server DISABLED: \(reason, privacy: .public)"
        )
    }
}
