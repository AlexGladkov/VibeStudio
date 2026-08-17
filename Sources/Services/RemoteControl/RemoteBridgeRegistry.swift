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
    func registerBridge(_ bridge: RemoteSessionBridge) {
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
            Logger.remoteControl.warning(
                "RemoteBridgeRegistry.registerBridge: no terminal view for session=\(bridge.sessionId)"
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

    /// Broadcast a JSON text message ONLY to bridges attached to a specific session.
    ///
    /// Used for cost_update messages — prevents devices on other sessions from
    /// receiving another session's activity data (BOLA protection).
    func broadcastToSession(_ sessionId: UUID, json: String) {
        for (_, bridge) in activeBridges where bridge.sessionId == sessionId {
            bridge.sendTextMessage(json)
        }
    }
}
