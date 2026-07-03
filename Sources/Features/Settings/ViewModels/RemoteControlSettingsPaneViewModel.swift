// MARK: - RemoteControlSettingsPaneViewModel
// Observable view model for the Remote Control settings pane.
// Owns the server/preferences orchestration (start/stop, port-restart policy,
// ngrok connect/disconnect, PIN regeneration, device disconnect) so the view
// stays declarative.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - RemoteControlSettingsPaneViewModel

/// Drives ``RemoteControlSettingsPane``.
///
/// All business orchestration that previously lived inline in the view's
/// `Binding` setters (enable-toggle → start/stop, port change → restart while
/// running, ngrok connect/disconnect, PIN regeneration, device disconnect) is
/// centralised here.
///
/// The `server` and `preferences` dependencies are held as **concrete**
/// `@Observable` types (not `any Protocol` existentials) so SwiftUI observation
/// remains transparent through the pass-through computed properties — reading
/// e.g. `viewModel.isRunning` in a view body registers a dependency on
/// `server.isRunning` exactly as a direct read would.
@Observable
@MainActor
final class RemoteControlSettingsPaneViewModel {

    // MARK: - Dependencies

    private let server: RemoteControlServer
    private let preferences: RemoteControlPreferences

    // MARK: - Init

    init(server: RemoteControlServer, preferences: RemoteControlPreferences) {
        self.server = server
        self.preferences = preferences
    }

    // MARK: - Preference State (observation-transparent)

    var isEnabled: Bool { preferences.remoteControlEnabled }
    var port: Int { preferences.remoteControlPort }
    var bindToLocalhost: Bool { preferences.bindToLocalhost }
    var bonjourEnabled: Bool { preferences.bonjourEnabled }
    var ngrokAuthtoken: String { preferences.ngrokAuthtoken }

    // MARK: - Server State (observation-transparent)

    var isRunning: Bool { server.isRunning }
    var isNgrokRunning: Bool { server.isNgrokRunning }
    var ngrokTunnelURL: String? { server.ngrokTunnelURL }
    var ngrokError: String? { server.ngrokError }
    var currentPin: String { server.currentPin }
    var serverPort: Int { server.port }
    var connectedDevices: [RemoteDevice] { server.connectedDevices }

    // MARK: - Enable / Disable

    /// Toggle the server on/off, starting or stopping it to match the new state.
    func setEnabled(_ newValue: Bool) {
        preferences.remoteControlEnabled = newValue
        if newValue {
            server.start()
        } else {
            server.stop()
        }
    }

    // MARK: - Port

    /// Persist a new listening port. If the server is currently running and the
    /// value actually changed, restart it so the new port takes effect.
    func setPort(_ newValue: Int) {
        let old = preferences.remoteControlPort
        preferences.remoteControlPort = newValue
        if server.isRunning && newValue != old {
            server.stop()
            server.start()
        }
    }

    // MARK: - Simple Preference Toggles

    func setBindToLocalhost(_ newValue: Bool) {
        preferences.bindToLocalhost = newValue
    }

    func setBonjourEnabled(_ newValue: Bool) {
        preferences.bonjourEnabled = newValue
    }

    func setNgrokAuthtoken(_ newValue: String) {
        preferences.ngrokAuthtoken = newValue
    }

    // MARK: - ngrok

    /// Enable and start the ngrok tunnel.
    func connectNgrok() {
        preferences.ngrokEnabled = true
        server.startNgrok()
    }

    /// Disable and tear down the ngrok tunnel.
    func disconnectNgrok() {
        preferences.ngrokEnabled = false
        server.stopNgrok()
    }

    // MARK: - PIN

    /// Regenerate the pairing PIN.
    func regeneratePin() {
        server.regeneratePin()
    }

    // MARK: - Devices

    /// Forcibly disconnect a paired device.
    func disconnect(deviceId: UUID) {
        server.disconnect(deviceId)
    }
}
