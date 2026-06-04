package studio.vibe.shared.service.remote

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import studio.vibe.shared.model.RemoteDevice

// ── PlatformLogger Interface ──────────────────────────────────────────────────

/**
 * Platform-injectable structured logger.
 *
 * Platform implementations map these calls to the appropriate system logging facility:
 * - macOS/iOS: `os_log` / `Logger`
 * - JVM: `java.util.logging` or SLF4J
 * - Browser: `console.*`
 */
interface PlatformLogger {
    fun info(tag: String, message: String)
    fun warning(tag: String, message: String)
    fun error(tag: String, message: String)
}

// ── RemoteAuditLog ────────────────────────────────────────────────────────────

/**
 * Centralised audit logging for all Remote Control security-relevant events.
 *
 * Every security event (authentication attempt, device connection/disconnection,
 * terminal input relay, server shutdown) is channelled through this class so that
 * audit trails are consistent and filterable by the `REMOTE_CONTROL` tag.
 *
 * Log levels:
 * - [PlatformLogger.info]    — successful operations (connect, disconnect, input relay).
 * - [PlatformLogger.warning] — per-IP lockout.
 * - [PlatformLogger.error]   — failed authentication attempts, global lockout.
 *
 * Terminal content is **never** logged — only the byte length of input payloads.
 * This prevents passwords, API keys, and other secrets from appearing in logs.
 */
@OptIn(ExperimentalUuidApi::class)
class RemoteAuditLog(private val logger: PlatformLogger) {

    companion object {
        private const val TAG = "REMOTE_CONTROL"
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /**
     * Log a PIN authentication attempt.
     *
     * @param ip      The client's IP address.
     * @param success Whether the PIN was accepted.
     */
    fun authAttempt(ip: String, success: Boolean) {
        if (success) {
            logger.info(TAG, "[AUDIT] Auth success from IP: $ip")
        } else {
            logger.error(TAG, "[AUDIT] Auth failure from IP: $ip")
        }
    }

    /**
     * Log a rate-limit lockout event.
     *
     * @param ip     The locked-out IP address.
     * @param global Whether this triggered a global (all-IP) lockout.
     */
    fun authLockout(ip: String, global: Boolean) {
        if (global) {
            logger.error(TAG, "[AUDIT] GLOBAL LOCKOUT triggered by IP: $ip")
        } else {
            logger.warning(TAG, "[AUDIT] IP lockout: $ip")
        }
    }

    // ── Device Lifecycle ──────────────────────────────────────────────────────

    /**
     * Log a successful device connection.
     *
     * @param device    The authenticated remote device.
     * @param sessionId The terminal session the device attached to.
     */
    fun deviceConnect(device: RemoteDevice, sessionId: Uuid) {
        logger.info(
            TAG,
            "[AUDIT] Device connected: ${device.displayName} (${device.ipAddress}) -> session $sessionId",
        )
    }

    /**
     * Log a device disconnection.
     *
     * @param deviceId The disconnected device's identifier.
     * @param reason   Human-readable reason (e.g. `"idle_timeout"`, `"kicked_by_host"`).
     */
    fun deviceDisconnect(deviceId: Uuid, reason: String) {
        logger.info(TAG, "[AUDIT] Device disconnected: $deviceId reason=$reason")
    }

    // ── Terminal I/O ──────────────────────────────────────────────────────────

    /**
     * Log terminal input relayed from a remote device.
     *
     * Only the **byte count** is logged — the content is never logged.
     * This prevents passwords, API keys, or other secrets from appearing in audit logs.
     *
     * @param deviceId  The device that sent the input.
     * @param sessionId The terminal session that received the input.
     * @param length    Number of bytes in the input payload.
     */
    fun terminalInput(deviceId: Uuid, sessionId: Uuid, length: Int) {
        logger.info(TAG, "[AUDIT] Input: device=$deviceId session=$sessionId bytes=$length")
    }

    // ── Server Lifecycle ──────────────────────────────────────────────────────

    /**
     * Log the server being disabled due to a security policy violation.
     *
     * @param reason Description of the triggering security event.
     */
    fun serverDisabled(reason: String) {
        logger.error(TAG, "[AUDIT] Server DISABLED: $reason")
    }
}
