// MARK: - RemoteSessionBridge
// Bridges a WebSocket connection to a terminal PTY session.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOWebSocket
import OSLog

/// Bridges a single WebSocket connection to a terminal PTY session.
///
/// Responsibilities:
/// - Subscribe to terminal output via `TaggedTerminalView.onRawData`
///   and relay raw PTY bytes to the WebSocket client as binary frames.
///   (The legacy `onLinesChanged` callback is no longer used — it was
///   replaced by `onRawData` to preserve ANSI escape sequences for xterm.js.
///   `RemoteBridgeRegistry.registerBridge` installs `onRawData` after BOLA
///   verification; `unregisterBridge` clears it on close.)
/// - Relay client input to the terminal via `TerminalService.sendInput`.
/// - Rate-limit client input to 120 messages/min.
/// - Enforce idle timeout and clean up on detach.
///
/// **Threading model:**
/// - All public methods are `@MainActor` (terminal services live on MainActor).
/// - Raw PTY output reaches ``handleRawData`` on the MainActor: SwiftTerm
///   delivers `dataReceived` on the main thread (its LocalProcess uses
///   `dispatchQueue: .main`), so there is no off-main PTY read thread here.
/// - WebSocket writes go through the cached `Channel` reference (Sendable)
///   using `channel.eventLoop.execute`.
@MainActor
final class RemoteSessionBridge {

    // MARK: - Public Properties

    /// The authenticated device that owns this bridge.
    let deviceId: UUID

    /// The terminal session this bridge is attached to.
    let sessionId: UUID

    // MARK: - Internal State

    /// Weak reference to the NIO channel for sending WebSocket frames.
    ///
    /// `Channel` is `Sendable`; `ChannelHandlerContext` is NOT.
    /// Internal visibility so ``RemoteControlServer`` can broadcast through it.
    weak var wsChannel: Channel?

    // MARK: - Private State

    /// The terminal service for sending input and reading scrollback.
    private let terminalService: TerminalService

    /// Idle timeout duration in minutes.
    private let idleTimeoutMinutes: Int

    /// Task that fires the idle timeout closure.
    private var idleTimer: Task<Void, Never>?

    /// Rate limiter: O(1) sliding window counter.
    /// Tracks message count and window start instead of storing all timestamps.
    private var rateLimitCount: Int = 0
    private var rateLimitWindowStart: Date = .distantPast

    /// Rate limit: maximum input messages per minute.
    private static let maxInputPerMinute = 120

    /// Output throttle window (ms): batch rapid PTY reads into one WS frame.
    private static let outputThrottleMilliseconds = 16

    /// Rate-limit sliding window duration (seconds).
    private static let rateLimitWindowSeconds: TimeInterval = 60

    /// Retry-After hint (ms) sent with a rate-limit warning.
    private static let rateLimitRetryAfterMilliseconds = 500

    /// Terminal resize clamps: allowed column range.
    private static let minColumns = 10
    private static let maxColumns = 500

    /// Terminal resize clamps: allowed row range.
    private static let minRows = 4
    private static let maxRows = 200

    /// Shared encoder for rate-limit messages.
    private let jsonEncoder = JSONEncoder()

    /// Output throttle: batch terminal output for 16ms before sending.
    private var outputBufferTask: Task<Void, Never>?
    private var pendingRawBytes: [UInt8] = []

    /// Whether streaming is active (prevents double-subscribe).
    private var isStreaming = false

    /// Set to true by `detach()` to permanently disable streaming.
    /// Once detached, `startStreaming()` becomes a no-op so that a late
    /// `startStreaming()` call after a BOLA rejection cannot re-arm the idle
    /// timer on a bridge whose NIO channel has already been closed.
    private var isDetached = false

    // MARK: - Init

    /// Create a new session bridge.
    ///
    /// - Parameters:
    ///   - deviceId: Authenticated device identifier.
    ///   - sessionId: Terminal session to bridge.
    ///   - channel: NIO channel for WebSocket frame output.
    ///   - terminalService: Terminal service for PTY I/O.
    ///   - idleTimeoutMinutes: Minutes before idle disconnect (default from preferences).
    init(
        deviceId: UUID,
        sessionId: UUID,
        channel: Channel,
        terminalService: TerminalService,
        idleTimeoutMinutes: Int
    ) {
        self.deviceId = deviceId
        self.sessionId = sessionId
        self.wsChannel = channel
        self.terminalService = terminalService
        self.idleTimeoutMinutes = idleTimeoutMinutes
    }

    // MARK: - Streaming

    /// Begin streaming terminal output to the WebSocket client.
    ///
    /// Sets the `isStreaming` flag so that ``handleRawData(_:)`` will forward
    /// PTY bytes. The `onRawData` callback is installed separately by
    /// `RemoteBridgeRegistry.registerBridge` after BOLA verification — only
    /// sessions that pass the guard actually receive output.
    ///
    /// This method is a no-op if ``detach()`` has already been called, which
    /// prevents a late `startStreaming()` invocation (after a BOLA rejection)
    /// from re-arming the idle timer on a bridge whose NIO channel is already
    /// closed.
    func startStreaming() {
        guard !isDetached, !isStreaming else { return }
        isStreaming = true

        resetIdleTimer()

        Logger.remoteControl.info(
            "RemoteSessionBridge: started streaming session=\(self.sessionId) device=\(self.deviceId)"
        )
    }

    /// Called with raw PTY output bytes (via `TaggedTerminalView.onRawData`).
    ///
    /// Relays unprocessed terminal data — including ANSI escape sequences —
    /// to the WebSocket client as binary frames. This allows the remote
    /// xterm.js to correctly render colors, cursor movements, and scrolling.
    ///
    /// Uses 16ms throttle to batch rapid successive PTY reads into a single
    /// WebSocket frame, reducing frame overhead.
    ///
    /// - Parameter data: Raw bytes from the PTY file descriptor.
    func handleRawData(_ data: ArraySlice<UInt8>) {
        // ARCH-C1: NSLog removed — was on PTY hot-path (thousands/sec).
        // Even under #if DEBUG it forced string interpolation + syscall per chunk.
        // For diagnostics use os_signpost or Logger.remoteControl.trace explicitly.
        guard isStreaming else { return }
        pendingRawBytes.append(contentsOf: data)

        if outputBufferTask == nil {
            outputBufferTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: .milliseconds(Self.outputThrottleMilliseconds))
                guard let self else { return }
                let batch = self.pendingRawBytes
                self.pendingRawBytes = []
                self.outputBufferTask = nil
                self.sendRawBinaryFrame(batch)
            }
        }
    }

    // MARK: - Input Handling

    /// Handle text input from the WebSocket client.
    ///
    /// Rate-limited to 120 messages/min. Excess messages are dropped with
    /// a `rate_limited` warning sent to the client.
    ///
    /// - Parameter data: UTF-8 text to relay to the PTY.
    func handleInput(_ data: String) {
        resetIdleTimer()

        // Rate limiting: O(1) sliding window counter (120 messages/min).
        let now = Date()
        if now.timeIntervalSince(rateLimitWindowStart) >= Self.rateLimitWindowSeconds {
            // Window expired — reset.
            rateLimitWindowStart = now
            rateLimitCount = 0
        }

        if rateLimitCount >= Self.maxInputPerMinute {
            sendRateLimitWarning()
            return
        }

        rateLimitCount += 1

        // Relay to PTY.
        terminalService.sendInput(data, to: sessionId)

        // Audit log (byte length only -- NEVER log content, which may contain secrets).
        RemoteAuditLog.terminalInput(
            deviceId: deviceId,
            sessionId: sessionId,
            length: data.utf8.count
        )
    }

    /// Handle terminal resize from the WebSocket client.
    ///
    /// - Parameters:
    ///   - cols: New column count (clamped to 10...500).
    ///   - rows: New row count (clamped to 4...200).
    func handleResize(cols: Int, rows: Int) {
        // SEC-M2: reject non-positive dimensions before clamping so a client
        // cannot probe the lower bound (or trigger PTY ioctl with bogus sizes
        // that some terminals interpret as "use default"). Clamping alone
        // would silently coerce `cols = -1` into `10`.
        guard cols > 0, rows > 0 else { return }
        resetIdleTimer()
        let clampedCols = min(max(cols, Self.minColumns), Self.maxColumns)
        let clampedRows = min(max(rows, Self.minRows), Self.maxRows)
        terminalService.resize(
            session: sessionId,
            to: TerminalSize(columns: clampedCols, rows: clampedRows)
        )
    }

    // MARK: - Broadcast

    /// Send a JSON text message (used by server for broadcast).
    ///
    /// - Parameter json: JSON string to send as a text WebSocket frame.
    func sendTextMessage(_ json: String) {
        sendTextFrame(json)
    }

    // MARK: - Cleanup

    /// Detach from the terminal session and clean up all internal bridge state.
    ///
    /// Cancels the idle timer and pending output tasks, resets streaming state,
    /// and permanently marks the bridge as detached so that any subsequent
    /// ``startStreaming()`` call is a no-op. Does NOT close the underlying NIO
    /// channel — callers that want to terminate the transport (e.g. BOLA
    /// rejection, explicit disconnect) must call ``closeTransport(code:reason:)``
    /// explicitly after `detach()`.
    ///
    /// `RemoteBridgeRegistry.unregisterBridge` is the canonical teardown path:
    /// it calls `detach()` and then clears the `onRawData` callback on the
    /// terminal view so no further PTY output reaches a dead bridge.
    func detach() {
        isDetached = true
        isStreaming = false
        idleTimer?.cancel()
        idleTimer = nil
        outputBufferTask?.cancel()
        outputBufferTask = nil
        pendingRawBytes = []
        rateLimitCount = 0

        Logger.remoteControl.info(
            "RemoteSessionBridge: detached session=\(self.sessionId) device=\(self.deviceId)"
        )
    }

    /// Close the underlying NIO channel with a WebSocket close frame.
    ///
    /// Used by rejection paths (BOLA guard, explicit disconnect) that need to
    /// terminate the transport after calling ``detach()``. The idle timer close
    /// path (``resetIdleTimer()``) also funnels through here.
    ///
    /// Idempotent: if `wsChannel` is already nil (channel already closed), this
    /// is a no-op.
    ///
    /// - Parameters:
    ///   - code: Application-defined WebSocket close code (RFC 6455 §7.4.2, range 4000-4999).
    ///   - reason: Human-readable close reason string.
    func closeTransport(code: UInt16, reason: String) {
        closeWithCode(code, reason: reason)
    }

    // MARK: - Private: WebSocket Output

    /// Send raw PTY bytes as a binary WebSocket frame.
    private func sendRawBinaryFrame(_ bytes: [UInt8]) {
        // ARCH-C1: NSLog removed (hot path — see handleRawData).
        guard !bytes.isEmpty, let ch = wsChannel else { return }
        var buffer = ch.allocator.buffer(capacity: bytes.count)
        buffer.writeBytes(bytes)
        let frame = WebSocketFrame(fin: true, opcode: .binary, data: buffer)

        ch.eventLoop.execute {
            ch.writeAndFlush(frame, promise: nil)
        }
    }

    /// Send a JSON text WebSocket frame.
    private func sendTextFrame(_ json: String) {
        guard let ch = wsChannel else { return }
        let bytes = [UInt8](json.utf8)
        var buffer = ch.allocator.buffer(capacity: bytes.count)
        buffer.writeBytes(bytes)
        let frame = WebSocketFrame(fin: true, opcode: .text, data: buffer)

        ch.eventLoop.execute {
            ch.writeAndFlush(frame, promise: nil)
        }
    }

    /// Send a rate-limit warning to the client.
    private func sendRateLimitWarning() {
        let msg = WSRateLimitedMessage(
            type: "rate_limited",
            message: "Input rate limit exceeded. Messages are being dropped.",
            retryAfterMs: Self.rateLimitRetryAfterMilliseconds
        )
        if let json = try? jsonEncoder.encode(msg),
           let str = String(data: json, encoding: .utf8) {
            sendTextFrame(str)
        }
    }

    // MARK: - Private: Idle Timeout

    /// Reset the idle timeout timer.
    private func resetIdleTimer() {
        idleTimer?.cancel()
        let timeoutMinutes = idleTimeoutMinutes
        idleTimer = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(timeoutMinutes * 60))
            guard let self, !Task.isCancelled else { return }
            Logger.remoteControl.info(
                "RemoteSessionBridge: idle timeout device=\(self.deviceId) session=\(self.sessionId)"
            )
            self.closeWithCode(WSCloseCode.heartbeatTimeout, reason: "Idle timeout")
        }
    }

    /// Close the WebSocket connection with a custom close code.
    ///
    /// Shared implementation used by both the idle-timer path and the public
    /// ``closeTransport(code:reason:)`` method. Idempotent when `wsChannel` is nil.
    private func closeWithCode(_ code: UInt16, reason: String) {
        guard let ch = wsChannel else { return }
        var buffer = ch.allocator.buffer(capacity: 2 + reason.utf8.count)
        buffer.writeInteger(code)
        buffer.writeString(reason)
        let frame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)

        ch.eventLoop.execute {
            ch.writeAndFlush(frame).whenComplete { _ in
                ch.close(promise: nil)
            }
        }
    }
}
