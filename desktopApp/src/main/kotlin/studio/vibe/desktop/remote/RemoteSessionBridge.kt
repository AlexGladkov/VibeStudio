@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package studio.vibe.desktop.remote

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import studio.vibe.desktop.terminal.DesktopTerminalService
import studio.vibe.shared.model.TerminalSize
import java.util.UUID
import java.util.logging.Logger
import kotlin.uuid.Uuid

/**
 * Bridges a single WebSocket session to a terminal PTY session.
 *
 * Matches Swift `RemoteSessionBridge` behavior:
 * - Subscribes to [DesktopTerminalService.outputFlow] for the session.
 * - Relays PTY output as binary WebSocket frames (raw bytes for xterm.js).
 * - Batches output in 16ms windows to reduce frame overhead.
 * - Rate-limits client input to 120 messages/minute.
 * - Enforces idle timeout, disconnecting inactive clients.
 *
 * Thread safety: all public methods are called from coroutine context.
 * [wsSession] is thread-safe via Ktor's internal channel.
 *
 * @param deviceId   The authenticated device (JVM [UUID]).
 * @param sessionId  The terminal session (JVM [UUID], converted to KMP [Uuid] when calling services).
 */
class RemoteSessionBridge(
    val deviceId: UUID,
    val sessionId: UUID,
    private val wsSession: DefaultWebSocketServerSession,
    private val terminalService: DesktopTerminalService,
    private val idleTimeoutMinutes: Int,
    private val bridgeScope: CoroutineScope,
) {
    companion object {
        private val log = Logger.getLogger("RemoteSessionBridge")
        private const val MAX_INPUT_PER_MINUTE = 120
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
        private const val OUTPUT_BATCH_DELAY_MS = 16L
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var isStreaming = false
    @Volatile private var isDetached = false

    private var outputJob: Job? = null
    private var idleTimerJob: Job? = null
    private var batchJob: Job? = null

    // Output batching buffer — written from output coroutine, accessed under lock.
    private val pendingOutputLock = Any()
    private val pendingOutput = mutableListOf<ByteArray>()

    // Rate limiting — sliding window counter (single coroutine context).
    private var rateLimitCount = 0
    private var rateLimitWindowStart = System.currentTimeMillis()

    // KMP Uuid for calls into shared KMP services.
    private val sessionKmpUuid: Uuid = Uuid.parse(sessionId.toString())

    // ── Streaming ──────────────────────────────────────────────────────────────

    /**
     * Begin streaming terminal output to the WebSocket client.
     * Subscribes to [DesktopTerminalService.outputFlow] for [sessionId].
     */
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true
        resetIdleTimer()

        outputJob = bridgeScope.launch(Dispatchers.IO) {
            val flow = terminalService.outputFlow(sessionKmpUuid) ?: return@launch
            flow.collect { chunk ->
                if (isDetached) return@collect
                val bytes = chunk.encodeToByteArray()
                synchronized(pendingOutputLock) {
                    pendingOutput.add(bytes)
                }
                scheduleBatch()
            }
        }

        log.info("RemoteSessionBridge: started streaming session=$sessionId device=$deviceId")
    }

    private fun scheduleBatch() {
        if (batchJob?.isActive == true) return
        batchJob = bridgeScope.launch {
            delay(OUTPUT_BATCH_DELAY_MS)
            flushOutput()
        }
    }

    private suspend fun flushOutput() {
        if (isDetached) return
        val bytes = synchronized(pendingOutputLock) {
            if (pendingOutput.isEmpty()) return
            val total = pendingOutput.sumOf { it.size }
            val merged = ByteArray(total)
            var pos = 0
            for (chunk in pendingOutput) {
                chunk.copyInto(merged, pos)
                pos += chunk.size
            }
            pendingOutput.clear()
            merged
        }
        runCatching {
            wsSession.send(Frame.Binary(true, bytes))
        }.onFailure {
            log.fine("RemoteSessionBridge: failed to send binary frame: ${it.message}")
        }
    }

    // ── Input handling ─────────────────────────────────────────────────────────

    /**
     * Handle text input from the WebSocket client.
     * Rate-limited to [MAX_INPUT_PER_MINUTE] messages/minute.
     * Max payload: 4096 bytes.
     */
    fun handleInput(data: String) {
        if (isDetached) return
        resetIdleTimer()

        val now = System.currentTimeMillis()
        if (now - rateLimitWindowStart >= RATE_LIMIT_WINDOW_MS) {
            rateLimitWindowStart = now
            rateLimitCount = 0
        }

        if (rateLimitCount >= MAX_INPUT_PER_MINUTE) {
            sendRateLimitWarning()
            return
        }

        if (data.encodeToByteArray().size > 4096) {
            log.warning("RemoteSessionBridge: input payload exceeds 4096 bytes, rejected")
            sendTextMessage("{\"type\":\"error\",\"message\":\"Input too large (max 4096 bytes)\"}")
            return
        }

        rateLimitCount++
        terminalService.sendInput(data, sessionKmpUuid)
    }

    /**
     * Handle terminal resize from the WebSocket client.
     * Cols clamped to 10..500, rows to 4..200.
     */
    fun handleResize(cols: Int, rows: Int) {
        if (isDetached) return
        resetIdleTimer()
        terminalService.resize(
            sessionId = sessionKmpUuid,
            size = TerminalSize(
                columns = cols.coerceIn(10, 500),
                rows = rows.coerceIn(4, 200),
            ),
        )
    }

    // ── Text broadcast ─────────────────────────────────────────────────────────

    /**
     * Send a JSON text message to the WebSocket client.
     */
    fun sendTextMessage(msg: String) {
        if (isDetached) return
        bridgeScope.launch {
            runCatching {
                wsSession.send(Frame.Text(msg))
            }
        }
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────

    /**
     * Detach from the terminal session and release all resources.
     */
    fun detach() {
        if (isDetached) return
        isDetached = true
        isStreaming = false

        outputJob?.cancel()
        idleTimerJob?.cancel()
        batchJob?.cancel()
        outputJob = null
        idleTimerJob = null
        batchJob = null

        synchronized(pendingOutputLock) { pendingOutput.clear() }
        rateLimitCount = 0

        log.info("RemoteSessionBridge: detached session=$sessionId device=$deviceId")
    }

    // ── Private: idle timer ────────────────────────────────────────────────────

    private fun resetIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = bridgeScope.launch {
            delay(idleTimeoutMinutes * 60_000L)
            if (!isDetached) {
                log.info("RemoteSessionBridge: idle timeout device=$deviceId session=$sessionId")
                runCatching {
                    wsSession.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Idle timeout"))
                }
            }
        }
    }

    private fun sendRateLimitWarning() {
        val msg = WSRateLimitedMessage(
            type = "rate_limited",
            message = "Input rate limit exceeded. Messages are being dropped.",
            retryAfterMs = 500,
        )
        sendTextMessage(json.encodeToString<WSRateLimitedMessage>(msg))
    }
}
