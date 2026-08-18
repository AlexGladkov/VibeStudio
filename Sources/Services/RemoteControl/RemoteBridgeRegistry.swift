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
///
/// **Index invariant (P1-7):** `sessionIdToBridge` is always kept in
/// sync with `activeBridges`. Every mutation that adds or removes an entry
/// in `activeBridges` MUST mirror that change in `sessionIdToBridge`.
/// This gives O(1) `sessionId → bridge` lookup used by BOLA checks and
/// session listings, removing the previous O(n) linear scan.
@Observable
@MainActor
final class RemoteBridgeRegistry {

    // MARK: - State

    /// Active session bridges keyed by device ID. Read by HTTP router (for
    /// BOLA enforcement, session listings, status) and SwiftUI views.
    private(set) var activeBridges: [UUID: RemoteSessionBridge] = [:]

    /// Secondary index: session ID → bridge for O(1) `sessionId`-based
    /// lookups. Always kept in sync with `activeBridges`.
    private(set) var sessionIdToBridge: [UUID: RemoteSessionBridge] = [:]

    // MARK: - Dependencies

    /// Required so we can install / clear `onRawData` on the terminal view
    /// associated with each bridge.
    private let terminalService: TerminalService

    /// Optional callback invoked whenever a bridge handles terminal input.
    /// Used by RemoteAuthService to track `lastActivity` per device (P2-5).
    var onInputActivity: ((UUID) -> Void)?

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
    ///
    /// **P1-6 fix:** if a bridge already exists for this `deviceId`, the old
    /// bridge is fully evicted (detached + `onRawData` cleared + removed from
    /// both indices) before the new one takes its place — preventing a leaked
    /// idle-timer Task and stale `onRawData` callbacks on the terminal view.
    func registerBridge(_ bridge: RemoteSessionBridge) {
        // BOLA / IDOR enforcement: verify the session exists in TerminalService
        // before wiring any output callbacks. A valid auth token does NOT grant
        // access to arbitrary session IDs — only live sessions are bridgeable.
        guard terminalService.session(for: bridge.sessionId) != nil else {
            Logger.remoteControl.error(
                "RemoteBridgeRegistry.registerBridge REJECTED: session=\(bridge.sessionId) does not exist — possible BOLA probe by device=\(bridge.deviceId)"
            )
            // Reset bridge-internal state then close the NIO channel immediately
            // (avoids a 60 s heartbeat-only close-path DoS on rejected connections).
            bridge.detach()
            bridge.closeTransport(code: WSCloseCode.authRequired, reason: "Session not found")
            return
        }

        // P1-6: evict existing bridge for the same deviceId before registering.
        if let old = activeBridges[bridge.deviceId], old !== bridge {
            _evict(old)
        }

        activeBridges[bridge.deviceId] = bridge
        sessionIdToBridge[bridge.sessionId] = bridge   // P1-7: maintain index

        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = { [weak self, weak bridge] _, slice in
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
            // P2-5: hook input path to update lastActivity via bridge's input
            // notification, wired through the registry's callback.
            bridge.onInputActivity = { [weak self] deviceId in
                self?.onInputActivity?(deviceId)
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
    /// view, and removes the entry from both `activeBridges` and
    /// `sessionIdToBridge`.
    func unregisterBridge(_ bridge: RemoteSessionBridge) {
        // Only remove the entries if they still point to THIS bridge — a newer
        // bridge for the same device/session may have already taken the slot
        // (P1-6: the eviction path in registerBridge already cleaned up before
        // the new bridge was inserted, so this guard is a belt-and-braces check
        // against stale `channelInactive` callbacks arriving after re-register).
        if activeBridges[bridge.deviceId] === bridge {
            activeBridges.removeValue(forKey: bridge.deviceId)
        }
        if sessionIdToBridge[bridge.sessionId] === bridge {
            sessionIdToBridge.removeValue(forKey: bridge.sessionId)
        }

        _evict(bridge)

        Logger.remoteControl.info(
            "Bridge unregistered: device=\(bridge.deviceId) session=\(bridge.sessionId)"
        )
    }

    /// Detach all bridges and clear the registry. Called on server stop.
    func detachAll() {
        for (_, bridge) in activeBridges {
            _evict(bridge)
        }
        activeBridges.removeAll()
        sessionIdToBridge.removeAll()
    }

    /// Remove a specific device's bridge without going through the bridge
    /// instance directly (used by the public `disconnect(_:)` API).
    func removeBridge(forDevice deviceId: UUID) {
        if let bridge = activeBridges[deviceId] {
            activeBridges.removeValue(forKey: deviceId)
            if sessionIdToBridge[bridge.sessionId] === bridge {
                sessionIdToBridge.removeValue(forKey: bridge.sessionId)
            }
            _evict(bridge)
        }
    }

    // MARK: - Lookup (P1-7: O(1) secondary index)

    /// Find the bridge for a session ID in O(1) using the secondary index.
    ///
    /// Replaces all previous `activeBridges.values.first { $0.sessionId == id }`
    /// linear scans in the HTTP handlers and observation loop.
    func bridge(forSession sessionId: UUID) -> RemoteSessionBridge? {
        sessionIdToBridge[sessionId]
    }

    // MARK: - Broadcast

    /// Broadcast a JSON text message to every active bridge.
    func broadcastTextMessage(_ json: String) {
        for (_, bridge) in activeBridges {
            bridge.sendTextMessage(json)
        }
    }

    /// Broadcast a JSON text message ONLY to bridges attached to a specific session.
    ///
    /// Used for cost_update messages — prevents devices on other sessions from
    /// receiving another session's activity data (BOLA protection).
    func broadcastToSession(_ sessionId: UUID, json: String) {
        for (_, bridge) in activeBridges where bridge.sessionId == sessionId {
            bridge.sendTextMessage(json)
        }
    }

    // MARK: - Private

    /// Perform the low-level detach + terminal cleanup for a bridge without
    /// touching the index dictionaries. Callers are responsible for updating
    /// `activeBridges` and `sessionIdToBridge` before or after calling this.
    private func _evict(_ bridge: RemoteSessionBridge) {
        bridge.detach()
        if let view = terminalService.terminalView(for: bridge.sessionId) {
            view.onRawData = nil
        }
    }
}
