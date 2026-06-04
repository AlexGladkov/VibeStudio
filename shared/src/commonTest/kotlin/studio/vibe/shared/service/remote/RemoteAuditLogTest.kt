package studio.vibe.shared.service.remote

import studio.vibe.shared.model.RemoteDevice
import studio.vibe.shared.testutil.FakePlatformLogger
import kotlin.test.*
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Unit tests for [RemoteAuditLog].
 *
 * [FakePlatformLogger] captures all log calls. Tests verify:
 * - Correct log levels are used per event type
 * - Message content contains required contextual fields
 * - [REMOTE_CONTROL] tag is always present
 * - Terminal content is never logged (only byte length)
 */
@OptIn(ExperimentalUuidApi::class)
class RemoteAuditLogTest {

    private lateinit var logger: FakePlatformLogger
    private lateinit var auditLog: RemoteAuditLog

    @BeforeTest
    fun setup() {
        logger = FakePlatformLogger()
        auditLog = RemoteAuditLog(logger)
    }

    @AfterTest
    fun teardown() {
        logger.reset()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeDevice(
        ip: String = "10.0.0.1",
        displayName: String = "iPhone",
    ): RemoteDevice = RemoteDevice(
        id = Uuid.random(),
        displayName = displayName,
        ipAddress = ip,
        connectedAt = Clock.System.now(),
    )

    // ── authAttempt ───────────────────────────────────────────────────────────

    @Test
    fun authAttempt_success_logsAtInfoLevel() {
        // Arrange + Act
        auditLog.authAttempt(ip = "10.0.0.1", success = true)

        // Assert
        assertEquals(1, logger.infoEntries.size)
        assertTrue(logger.errorEntries.isEmpty())
    }

    @Test
    fun authAttempt_success_messageContainsIp() {
        // Arrange + Act
        auditLog.authAttempt(ip = "192.168.1.5", success = true)

        // Assert
        val message = logger.infoEntries.first().message
        assertTrue(message.contains("192.168.1.5"), "IP must appear in log message: $message")
    }

    @Test
    fun authAttempt_failure_logsAtErrorLevel() {
        // Arrange + Act
        auditLog.authAttempt(ip = "10.0.0.1", success = false)

        // Assert
        assertEquals(1, logger.errorEntries.size)
        assertTrue(logger.infoEntries.isEmpty())
    }

    @Test
    fun authAttempt_failure_messageContainsIp() {
        // Arrange + Act
        auditLog.authAttempt(ip = "172.16.0.1", success = false)

        // Assert
        val message = logger.errorEntries.first().message
        assertTrue(message.contains("172.16.0.1"))
    }

    @Test
    fun authAttempt_alwaysUsesRemoteControlTag() {
        // Arrange + Act
        auditLog.authAttempt(ip = "1.1.1.1", success = true)
        auditLog.authAttempt(ip = "1.1.1.1", success = false)

        // Assert
        logger.entries.forEach { entry ->
            assertEquals("REMOTE_CONTROL", entry.tag, "All entries must use REMOTE_CONTROL tag")
        }
    }

    @Test
    fun authAttempt_successMessage_containsAuditMarker() {
        auditLog.authAttempt(ip = "1.1.1.1", success = true)
        assertTrue(logger.infoEntries.first().message.contains("[AUDIT]"))
    }

    @Test
    fun authAttempt_failureMessage_containsAuditMarker() {
        auditLog.authAttempt(ip = "1.1.1.1", success = false)
        assertTrue(logger.errorEntries.first().message.contains("[AUDIT]"))
    }

    // ── authLockout ───────────────────────────────────────────────────────────

    @Test
    fun authLockout_global_logsAtErrorLevel() {
        // Arrange + Act
        auditLog.authLockout(ip = "10.0.0.1", global = true)

        // Assert
        assertEquals(1, logger.errorEntries.size)
        assertTrue(logger.warningEntries.isEmpty())
    }

    @Test
    fun authLockout_global_messageContainsIpAndGlobalKeyword() {
        // Arrange + Act
        auditLog.authLockout(ip = "10.0.0.2", global = true)

        // Assert
        val message = logger.errorEntries.first().message
        assertTrue(message.contains("10.0.0.2"))
        assertTrue(
            message.uppercase().contains("GLOBAL"),
            "Global lockout message must mention GLOBAL: $message",
        )
    }

    @Test
    fun authLockout_perIp_logsAtWarningLevel() {
        // Arrange + Act
        auditLog.authLockout(ip = "10.0.0.1", global = false)

        // Assert
        assertEquals(1, logger.warningEntries.size)
        assertTrue(logger.errorEntries.isEmpty())
    }

    @Test
    fun authLockout_perIp_messageContainsIp() {
        // Arrange + Act
        auditLog.authLockout(ip = "172.20.0.5", global = false)

        // Assert
        val message = logger.warningEntries.first().message
        assertTrue(message.contains("172.20.0.5"))
    }

    @Test
    fun authLockout_alwaysUsesRemoteControlTag() {
        auditLog.authLockout(ip = "1.1.1.1", global = false)
        auditLog.authLockout(ip = "1.1.1.1", global = true)
        logger.entries.forEach { assertEquals("REMOTE_CONTROL", it.tag) }
    }

    // ── deviceConnect ─────────────────────────────────────────────────────────

    @Test
    fun deviceConnect_logsAtInfoLevel() {
        // Arrange
        val device = makeDevice(ip = "192.168.1.10", displayName = "iPad")
        val sessionId = Uuid.random()

        // Act
        auditLog.deviceConnect(device, sessionId)

        // Assert
        assertEquals(1, logger.infoEntries.size)
    }

    @Test
    fun deviceConnect_messageContainsDeviceNameIpAndSessionId() {
        // Arrange
        val device = makeDevice(ip = "10.0.0.1", displayName = "Mac")
        val sessionId = Uuid.random()

        // Act
        auditLog.deviceConnect(device, sessionId)

        // Assert
        val message = logger.infoEntries.first().message
        assertTrue(message.contains("Mac"), "Message must contain device name: $message")
        assertTrue(message.contains("10.0.0.1"), "Message must contain IP: $message")
        assertTrue(message.contains(sessionId.toString()), "Message must contain session ID: $message")
    }

    @Test
    fun deviceConnect_usesRemoteControlTag() {
        auditLog.deviceConnect(makeDevice(), Uuid.random())
        assertEquals("REMOTE_CONTROL", logger.infoEntries.first().tag)
    }

    // ── deviceDisconnect ──────────────────────────────────────────────────────

    @Test
    fun deviceDisconnect_logsAtInfoLevel() {
        // Arrange
        val deviceId = Uuid.random()

        // Act
        auditLog.deviceDisconnect(deviceId, reason = "idle_timeout")

        // Assert
        assertEquals(1, logger.infoEntries.size)
    }

    @Test
    fun deviceDisconnect_messageContainsDeviceIdAndReason() {
        // Arrange
        val deviceId = Uuid.random()

        // Act
        auditLog.deviceDisconnect(deviceId, reason = "kicked_by_host")

        // Assert
        val message = logger.infoEntries.first().message
        assertTrue(message.contains(deviceId.toString()), "Message must contain device ID")
        assertTrue(message.contains("kicked_by_host"), "Message must contain reason: $message")
    }

    @Test
    fun deviceDisconnect_usesRemoteControlTag() {
        auditLog.deviceDisconnect(Uuid.random(), reason = "test")
        assertEquals("REMOTE_CONTROL", logger.infoEntries.first().tag)
    }

    // ── terminalInput ─────────────────────────────────────────────────────────

    @Test
    fun terminalInput_logsAtInfoLevel() {
        // Arrange + Act
        auditLog.terminalInput(
            deviceId = Uuid.random(),
            sessionId = Uuid.random(),
            length = 42,
        )

        // Assert
        assertEquals(1, logger.infoEntries.size)
    }

    @Test
    fun terminalInput_messageContainsDeviceIdSessionIdAndByteLength() {
        // Arrange
        val deviceId = Uuid.random()
        val sessionId = Uuid.random()

        // Act
        auditLog.terminalInput(deviceId, sessionId, length = 128)

        // Assert
        val message = logger.infoEntries.first().message
        assertTrue(message.contains(deviceId.toString()), "Message must contain device ID")
        assertTrue(message.contains(sessionId.toString()), "Message must contain session ID")
        assertTrue(message.contains("128"), "Message must contain byte length")
    }

    @Test
    fun terminalInput_doesNotLogActualContent() {
        // Arrange — simulate some terminal content that should NOT be logged
        val secretCommand = "export AWS_SECRET_KEY=super_secret_value"
        val deviceId = Uuid.random()
        val sessionId = Uuid.random()

        // Act — we can only pass length, not actual content; this test verifies the API shape
        auditLog.terminalInput(deviceId, sessionId, length = secretCommand.length)

        // Assert — the content string is not present in any log entry
        logger.entries.forEach { entry ->
            assertFalse(
                entry.message.contains("super_secret_value"),
                "Terminal content must never appear in logs: ${entry.message}",
            )
            assertFalse(
                entry.message.contains("AWS_SECRET_KEY"),
                "Terminal content must never appear in logs: ${entry.message}",
            )
        }
    }

    @Test
    fun terminalInput_usesRemoteControlTag() {
        auditLog.terminalInput(Uuid.random(), Uuid.random(), length = 5)
        assertEquals("REMOTE_CONTROL", logger.infoEntries.first().tag)
    }

    // ── serverDisabled ────────────────────────────────────────────────────────

    @Test
    fun serverDisabled_logsAtErrorLevel() {
        // Arrange + Act
        auditLog.serverDisabled(reason = "global_lockout")

        // Assert
        assertEquals(1, logger.errorEntries.size)
    }

    @Test
    fun serverDisabled_messageContainsReason() {
        // Arrange + Act
        auditLog.serverDisabled(reason = "excessive_failures")

        // Assert
        val message = logger.errorEntries.first().message
        assertTrue(message.contains("excessive_failures"), "Message must contain reason: $message")
    }

    @Test
    fun serverDisabled_messageContainsDisabledKeyword() {
        // Arrange + Act
        auditLog.serverDisabled(reason = "test")

        // Assert
        val message = logger.errorEntries.first().message
        assertTrue(
            message.uppercase().contains("DISABLED"),
            "Message must contain DISABLED: $message",
        )
    }

    @Test
    fun serverDisabled_usesRemoteControlTag() {
        auditLog.serverDisabled(reason = "test")
        assertEquals("REMOTE_CONTROL", logger.errorEntries.first().tag)
    }

    // ── AUDIT marker is always present ────────────────────────────────────────

    @Test
    fun allMethods_alwaysIncludeAuditMarkerInMessage() {
        // Arrange
        val device = makeDevice()
        val sessionId = Uuid.random()
        val deviceId = Uuid.random()

        // Act — call every public method
        auditLog.authAttempt(ip = "1.1.1.1", success = true)
        auditLog.authAttempt(ip = "1.1.1.1", success = false)
        auditLog.authLockout(ip = "1.1.1.1", global = false)
        auditLog.authLockout(ip = "1.1.1.1", global = true)
        auditLog.deviceConnect(device, sessionId)
        auditLog.deviceDisconnect(deviceId, reason = "test")
        auditLog.terminalInput(deviceId, sessionId, length = 10)
        auditLog.serverDisabled(reason = "reason")

        // Assert — every logged message must include [AUDIT]
        logger.entries.forEach { entry ->
            assertTrue(
                entry.message.contains("[AUDIT]"),
                "Every audit log message must contain [AUDIT] prefix. Found: '${entry.message}'",
            )
        }
    }
}
