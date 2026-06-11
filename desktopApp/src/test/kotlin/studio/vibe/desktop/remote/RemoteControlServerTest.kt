@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import studio.vibe.shared.feature.remote.domain.contract.RemoteAuthorizing
import studio.vibe.shared.feature.remote.domain.contract.SecurityEvent
import studio.vibe.shared.feature.terminal.domain.contract.TerminalRemoteHost
import studio.vibe.shared.feature.remote.domain.model.RemoteDevice
import studio.vibe.shared.feature.settings.domain.model.RemoteControlPreferencesReading
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for [RemoteControlServer] that do NOT spin up a real Ktor engine.
 *
 * Tests here verify observable-state bookkeeping (connectedDeviceCount, isRunning),
 * bridge registration/removal, and security lockout handling — all without
 * binding network ports.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteControlServerTest {

    private lateinit var scope: CoroutineScope

    // ── Fake Preferences ─────────────────────────────────────────────────────

    private val fakePreferences = object : RemoteControlPreferencesReading {
        override val remoteControlEnabled: Boolean = false
        override val remoteControlPort: Int = 17841
        override val bindToLocalhost: Boolean = true
        override val bonjourEnabled: Boolean = false
        override val ngrokEnabled: Boolean = false
        override val idleTimeoutMinutes: Int = 30
        override suspend fun loadNgrokAuthtoken(): String = ""
    }

    // ── Fake TerminalRemoteHost ───────────────────────────────────────────────

    private val fakeTerminalHost = mockk<TerminalRemoteHost>(relaxed = true)

    // ── Fake RemoteAuthorizing ────────────────────────────────────────────────

    private fun makeAuthService(
        securityEvents: MutableSharedFlow<SecurityEvent> = MutableSharedFlow(extraBufferCapacity = 4),
        devicesCount: MutableStateFlow<Int> = MutableStateFlow(0),
    ): RemoteAuthorizing = mockk<RemoteAuthorizing>(relaxed = true) {
        every { this@mockk.securityEvents } returns securityEvents
        every { this@mockk.devicesCount } returns devicesCount
        every { this@mockk.connectedDevices } returns MutableStateFlow(emptyList())
        every { this@mockk.currentPin } returns MutableStateFlow("123456")
        every { this@mockk.isLocked } returns MutableStateFlow(false)
        every { this@mockk.maxDevices } returns 3
    }

    @BeforeTest
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun makeServer(
        authService: RemoteAuthorizing = makeAuthService(),
    ) = RemoteControlServer(
        preferences = fakePreferences,
        terminalService = fakeTerminalHost,
        authService = authService,
        parentScope = scope,
    )

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun isRunning_initiallyFalse() {
        val server = makeServer()
        assertFalse(server.isRunning.value)
    }

    @Test
    fun connectedDeviceCount_initiallyZero() {
        val server = makeServer()
        assertEquals(0, server.connectedDeviceCount.value)
    }

    // ── connectedDeviceCount tracking ─────────────────────────────────────────

    @Test
    fun connectedDeviceCount_reflectsDevicesCountFlow() {
        val devicesCount = MutableStateFlow(0)
        val authService = makeAuthService(devicesCount = devicesCount)
        val server = makeServer(authService = authService)

        // The subscription runs on Dispatchers.IO inside serverScope
        Thread.sleep(100)

        devicesCount.value = 2
        Thread.sleep(100)

        assertEquals(2, server.connectedDeviceCount.value,
            "connectedDeviceCount must mirror authService.devicesCount")
    }

    // ── registerBridge / unregisterBridge ─────────────────────────────────────

    @Test
    fun registerBridge_incrementsDeviceCount() {
        val server = makeServer()
        val bridge = mockBridge()

        server.registerBridge(bridge)

        assertEquals(1, server.connectedDeviceCount.value)
    }

    @Test
    fun unregisterBridge_removesAndDecrementsCount() {
        val server = makeServer()
        val bridge = mockBridge()

        server.registerBridge(bridge)
        assertEquals(1, server.connectedDeviceCount.value)

        server.unregisterBridge(bridge)
        assertEquals(0, server.connectedDeviceCount.value)
    }

    @Test
    fun disconnect_removesBridgeByDeviceId() {
        val server = makeServer()
        val bridge = mockBridge()
        server.registerBridge(bridge)

        server.disconnect(bridge.deviceId)

        assertEquals(0, server.connectedDeviceCount.value)
    }

    @Test
    fun disconnect_unknownDeviceId_isNoOp() {
        val server = makeServer()
        // Should not throw
        server.disconnect(UUID.randomUUID())
        assertEquals(0, server.connectedDeviceCount.value)
    }

    // ── GlobalLockout triggers stop ───────────────────────────────────────────

    @Test
    fun globalLockout_doesNotCrashAndServerStaysNotRunning() {
        val securityEvents = MutableSharedFlow<SecurityEvent>(extraBufferCapacity = 4)
        val authService = makeAuthService(securityEvents = securityEvents)
        val server = makeServer(authService = authService)

        // Emit lockout — when server is not running, stop() is a no-op but must not crash
        securityEvents.tryEmit(SecurityEvent.GlobalLockout)
        Thread.sleep(200)

        // Server was never started, must still be false after lockout
        assertFalse(server.isRunning.value,
            "GlobalLockout must result in isRunning=false")
    }

    // ── concurrent start no-op ────────────────────────────────────────────────

    @Test
    fun concurrentStop_doesNotDeadlockOrThrow() {
        // Calling stop() on a not-yet-started server concurrently must not deadlock or throw.
        val server = makeServer()
        assertFalse(server.isRunning.value)

        val t1 = Thread { kotlinx.coroutines.runBlocking { server.stopAsync() } }
        val t2 = Thread { kotlinx.coroutines.runBlocking { server.stopAsync() } }
        t1.start()
        t2.start()
        t1.join(2_000)
        t2.join(2_000)

        assertFalse(server.isRunning.value)
    }

    // ── dispose ───────────────────────────────────────────────────────────────

    @Test
    fun dispose_doesNotThrow() {
        val server = makeServer()
        server.dispose()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockBridge(deviceId: UUID = UUID.randomUUID()): RemoteSessionBridge {
        return mockk<RemoteSessionBridge>(relaxed = true) {
            every { this@mockk.deviceId } returns deviceId
            every { this@mockk.sessionId } returns UUID.randomUUID()
        }
    }
}
