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
/// - Subscribe to terminal output via `TaggedTerminalView.onLinesChanged`
///   and relay changed lines to the WebSocket client as binary frames.
/// - Relay client input to the terminal via `TerminalService.sendInput`.
/// - Rate-limit client input to 120 messages/min.
/// - Enforce idle timeout and clean up on detach.
///
/// **Threading model:**
/// - All public methods are `@MainActor` (terminal services live on MainActor).
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

    /// Sliding window of input message timestamps for rate limiting.
    /// Capped at 120 entries per minute.
    private var inputTimestamps: [Date] = []

    /// Rate limit: maximum input messages per minute.
    private static let maxInputPerMinute = 120

    /// Output throttle: batch terminal output for 16ms before sending.
    private var outputBufferTask: Task<Void, Never>?
    private var pendingRawBytes: [UInt8] = []

    /// Whether streaming is active (prevents double-subscribe).
    private var isStreaming = false

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
    /// Subscribes to `TaggedTerminalView.onLinesChanged` for the session.
    /// When lines change, reads the terminal buffer and sends as binary frames.
    func startStreaming() {
        guard !isStreaming else { return }
        isStreaming = true

        resetIdleTimer()

        // The `onLinesChanged` callback on TaggedTerminalView is installed
        // by RemoteControlServer when registering this bridge.
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
        guard isStreaming else { return }

        pendingRawBytes.append(contentsOf: data)

        if outputBufferTask == nil {
            outputBufferTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: .milliseconds(16))
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

        // Rate limiting: 120 messages/min sliding window.
        let now = Date()
        let oneMinuteAgo = now.addingTimeInterval(-60)
        inputTimestamps.removeAll { $0 < oneMinuteAgo }

        if inputTimestamps.count >= Self.maxInputPerMinute {
            sendRateLimitWarning()
            return
        }

        inputTimestamps.append(now)

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
        resetIdleTimer()
        let clampedCols = min(max(cols, 10), 500)
        let clampedRows = min(max(rows, 4), 200)
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

    /// Detach from the terminal session and clean up all resources.
    func detach() {
        isStreaming = false
        idleTimer?.cancel()
        idleTimer = nil
        outputBufferTask?.cancel()
        outputBufferTask = nil
        pendingRawBytes = []
        inputTimestamps = []

        Logger.remoteControl.info(
            "RemoteSessionBridge: detached session=\(self.sessionId) device=\(self.deviceId)"
        )
    }

    // MARK: - Private: WebSocket Output

    /// Send raw PTY bytes as a binary WebSocket frame.
    private func sendRawBinaryFrame(_ bytes: [UInt8]) {
        guard !bytes.isEmpty, let ch = wsChannel else { return }
        var buffer = ch.allocator.buffer(capacity: bytes.count)
        buffer.writeBytes(bytes)
        let frame = WebSocketFrame(fin: true, opcode: .binary, data: buffer)

        ch.eventLoop.execute {
            ch.writeAndFlush(NIOAny(frame), promise: nil)
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
            ch.writeAndFlush(NIOAny(frame), promise: nil)
        }
    }

    /// Send a rate-limit warning to the client.
    private func sendRateLimitWarning() {
        let msg = WSRateLimitedMessage(
            type: "rate_limited",
            message: "Input rate limit exceeded. Messages are being dropped.",
            retryAfterMs: 500
        )
        if let json = try? JSONEncoder().encode(msg),
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
            self.closeWithCode(4004, reason: "Idle timeout")
        }
    }

    /// Close the WebSocket connection with a custom close code.
    private func closeWithCode(_ code: UInt16, reason: String) {
        guard let ch = wsChannel else { return }
        var buffer = ch.allocator.buffer(capacity: 2 + reason.utf8.count)
        buffer.writeInteger(code)
        buffer.writeString(reason)
        let frame = WebSocketFrame(fin: true, opcode: .connectionClose, data: buffer)

        ch.eventLoop.execute {
            ch.writeAndFlush(NIOAny(frame)).whenComplete { _ in
                ch.close(promise: nil)
            }
        }
    }
}
