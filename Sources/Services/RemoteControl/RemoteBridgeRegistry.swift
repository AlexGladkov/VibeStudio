// MARK: - RemoteBridgeRegistry
// ARCH-H7: Extracted from RemoteControlServer to isolate the lifecycle
// of WebSocket session bridges (register / unregister / broadcast) from
// the server lifecycle (start / stop / TLS). Server keeps a single
// `bridges` property and exposes a pass-through `activeBridges` for the
// router / views — but all mutation logic now lives here.
//
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// Owns the map of currently-attached ``RemoteSessionBridge`` instances and
/// the wiring between PTY output and the WebSocket transport.
///
/// **Threading model:** `@Observable @MainActor` so SwiftUI views can
/// observe `activeBridges` directly (e.g. badge count, attached-device
/// indicators). Bridge mutations are always on MainActor; the underlying
/// `RemoteSessionBridge` handles its own NIO hop for WebSocket writes.
@Observable
@MainActor
final class RemoteBridgeRegistry {

    // MARK: - State

    /// Active session bridges keyed by device ID. Read by HTTP router (for
    /// BOLA enforcement, session listings, status) and SwiftUI views.
    private(set) var activeBridges: [UUID: RemoteSessionBridge] = [:]

    // MARK: - Dependencies

    /// Required so we can install / clear `onRawData` on the terminal view
    /// associated with each bridge.
    private let terminalService: TerminalService

    // MARK: - Init

    init(terminalService: TerminalService) {
        self.terminalService = terminalService
    }

    // MARK: - Registration

    /// Register a new session bridge.
    ///
    /// Installs `onRawData` on the terminal view so unprocessed ANSI bytes
    /// flow to the WebSocket. Idempotent: re-registering the same bridge
    /// overwrites the previous entry.
    ///
    /// **BOLA guard:** the bridge's `sessionId` must correspond to a live session
    /// tracked by `TerminalService`. If the session does not exist (e.g. a client
    /// supplies a fabricated UUID to probe another user's PTY), the bridge is
    /// rejected: `detach()` resets bridge-internal state and `closeTransport`
    /// sends a WebSocket close frame and shuts the NIO channel immediately.
    /// The bridge is NOT added to `activeBridges`. Without this check an
    /// authenticated device could connect to `GET /api/v1/terminal/<any-uuid>`
    /// and silently receive raw PTY bytes from any session — a Broken
    /// Object-Level Authorization (BOLA / IDOR) vulnerability.
    func registerBridge(_ bridge: RemoteSessionBridge) {
        // BOLA / IDOR enforcement: verify the session exists in TerminalService
        // before wiring any output callbacks. A valid auth token does NOT grant
        // access to arbitrary session IDs — only live sessions are bridgeable.
        guard terminalService.session(for: bridge.sessionId) != nil else {
            Logger.remoteControl.error(
                "RemoteBridgeRegistry.registerBridge REJECTED: session=\(bridge.sessionId) does not exist — possible BOLA probe by device=\(bridge.deviceId)"
            )
            // Reset bridge-internal state (cancels idle timer, clears streaming
            // flag, drops pending output) then close the NIO channel so the TCP
            // socket is freed immediately. Without `closeTransport` the only
            // remaining close-path is the handler's heartbeat (60 s), enabling a
            // resource-exhaustion DoS: each rejected connection with a valid token
            // but fabricated sessionId occupies a live socket for up to 60 s.
            bridge.detach()
            bridge.closeTransport(code: WSCloseCode.authRequired, reason: "Session not found")
            return
        }

        activeBridges[bridge.deviceId] = bridge

        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = { [weak bridge] _, slice in
                guard let bridge else { return }
                // SwiftTerm delivers `dataReceived` on the MAIN thread (its
                // LocalProcess is created with `dispatchQueue: .main`), so we are
                // already on the MainActor here. The `Task { @MainActor }` hop is
                // therefore redundant — kept only because it is harmless and lets
                // `handleRawData` stay `@MainActor`-isolated without an explicit
                // `assumeIsolated`.
                Task { @MainActor in
                    bridge.handleRawData(slice)
                }
            }
        } else {
            // Session exists in TerminalService but view is not yet in the cache
            // (e.g. the session was just created and the view has not been attached
            // to a window yet). The bridge will receive output once the view is
            // attached and `onRawData` can be installed. This is a benign transient
            // state — the bridge's isStreaming guard prevents data loss.
            Logger.remoteControl.warning(
                "RemoteBridgeRegistry.registerBridge: no terminal view for session=\(bridge.sessionId) (view not yet attached to window)"
            )
        }

        Logger.remoteControl.info(
            "Bridge registered: device=\(bridge.deviceId) session=\(bridge.sessionId)"
        )
    }

    /// Unregister a session bridge (called on WebSocket close).
    ///
    /// Detaches the bridge, clears the `onRawData` callback on the terminal
    /// view, and removes the entry from `activeBridges`.
    func unregisterBridge(_ bridge: RemoteSessionBridge) {
        bridge.detach()

        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = nil
        }

        activeBridges.removeValue(forKey: bridge.deviceId)
        Logger.remoteControl.info(
            "Bridge unregistered: device=\(bridge.deviceId) session=\(bridge.sessionId)"
        )
    }

    /// Detach all bridges and clear the registry. Called on server stop.
    func detachAll() {
        for (_, bridge) in activeBridges {
            bridge.detach()
        }
        activeBridges.removeAll()
    }

    /// Remove a specific device's bridge without going through the bridge
    /// instance directly (used by the public `disconnect(_:)` API).
    func removeBridge(forDevice deviceId: UUID) {
        if let bridge = activeBridges[deviceId] {
            bridge.detach()
            activeBridges.removeValue(forKey: deviceId)
        }
    }

    // MARK: - Broadcast

    /// Broadcast a JSON text message to every active bridge.
    func broadcastTextMessage(_ json: String) {
        for (_, bridge) in activeBridges {
            bridge.sendTextMessage(json)
        }
    }
}
