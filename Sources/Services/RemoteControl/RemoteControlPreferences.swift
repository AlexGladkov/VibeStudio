// MARK: - RemoteControlPreferences
// UserDefaults-backed Remote Control server preferences.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import Security

/// UserDefaults-backed preferences for the embedded Remote Control server.
///
/// Keys are prefixed with `vs_remote_` to avoid collisions with other services.
/// Injected via `@Environment(\.remoteControlPreferences)` -- concrete type for
/// `@Observable` tracking (same pattern as `GeneralPreferences`).
@Observable
@MainActor
final class RemoteControlPreferences {

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let enabled = "vs_remote_control_enabled"
        static let port = "vs_remote_control_port"
        static let bindToLocalhost = "vs_remote_bind_localhost"
        static let bonjourEnabled = "vs_remote_bonjour_enabled"
        static let idleTimeoutMinutes = "vs_remote_idle_timeout"
        static let ngrokEnabled = "vs_remote_ngrok_enabled"
    }

    // MARK: - Preferences

    /// Whether the Remote Control HTTP/WS server is enabled. Default: `false`.
    var remoteControlEnabled: Bool {
        didSet { defaults.set(remoteControlEnabled, forKey: Keys.enabled) }
    }

    /// TCP port for the HTTP/WS server. Default: `7842`. Range: 1024...65535.
    var remoteControlPort: Int {
        didSet {
            let clamped = min(max(remoteControlPort, 1024), 65535)
            if clamped != remoteControlPort { remoteControlPort = clamped }
            defaults.set(clamped, forKey: Keys.port)
        }
    }

    /// Bind the server to localhost only (127.0.0.1).
    ///
    /// **SECURITY:** Default `false` (LAN access enabled for phone control).
    /// When set to `true`, the server is unreachable from other devices on the
    /// LAN -- only local connections and SSH tunnels can reach it.
    var bindToLocalhost: Bool {
        didSet { defaults.set(bindToLocalhost, forKey: Keys.bindToLocalhost) }
    }

    /// Whether Bonjour (`_vibestudio._tcp`) service advertisement is enabled.
    ///
    /// **SECURITY:** Default `false`. Bonjour broadcasts the server's presence
    /// on the local network. Only enable if `bindToLocalhost` is also `false`.
    var bonjourEnabled: Bool {
        didSet { defaults.set(bonjourEnabled, forKey: Keys.bonjourEnabled) }
    }

    /// Whether ngrok tunnel is enabled for remote access outside LAN.
    ///
    /// **SECURITY:** Default `false`. When enabled, the local server is exposed
    /// to the internet via ngrok. PIN authentication still required.
    var ngrokEnabled: Bool {
        didSet { defaults.set(ngrokEnabled, forKey: Keys.ngrokEnabled) }
    }

    /// ngrok authtoken for authenticated tunnels.
    ///
    /// Required since ngrok v3. Get one for free at https://dashboard.ngrok.com/get-started/your-authtoken
    var ngrokAuthtoken: String {
        didSet { KeychainHelper.save(account: "vs_ngrok_authtoken", value: ngrokAuthtoken) }
    }

    /// Idle timeout in minutes before disconnecting inactive remote clients.
    /// Default: `30`.
    var idleTimeoutMinutes: Int {
        didSet {
            let clamped = max(idleTimeoutMinutes, 1)
            if clamped != idleTimeoutMinutes { idleTimeoutMinutes = clamped }
            defaults.set(clamped, forKey: Keys.idleTimeoutMinutes)
        }
    }

    // MARK: - Init

    init() {
        // remoteControlEnabled defaults to false on first launch (SECURITY: opt-in only)
        remoteControlEnabled = defaults.object(forKey: Keys.enabled) == nil
            ? false
            : defaults.bool(forKey: Keys.enabled)

        // remoteControlPort defaults to 7842
        remoteControlPort = defaults.object(forKey: Keys.port) == nil
            ? 7842
            : defaults.integer(forKey: Keys.port)

        // bindToLocalhost defaults to false — Remote Control is for phone access over LAN
        bindToLocalhost = defaults.object(forKey: Keys.bindToLocalhost) == nil
            ? false
            : defaults.bool(forKey: Keys.bindToLocalhost)

        // bonjourEnabled defaults to false (SECURITY: off by default)
        bonjourEnabled = defaults.object(forKey: Keys.bonjourEnabled) == nil
            ? false
            : defaults.bool(forKey: Keys.bonjourEnabled)

        // ngrokEnabled defaults to false (SECURITY: opt-in only)
        ngrokEnabled = defaults.object(forKey: Keys.ngrokEnabled) == nil
            ? false
            : defaults.bool(forKey: Keys.ngrokEnabled)

        // Migrate from UserDefaults to Keychain (one-time).
        // SEC-L3: only delete the UserDefaults copy AFTER Keychain confirms
        // the write — otherwise a crash between the two ops could leave the
        // token in plaintext UserDefaults forever.
        if let legacyToken = defaults.string(forKey: "vs_remote_ngrok_authtoken"), !legacyToken.isEmpty {
            let saved = KeychainHelper.save(account: "vs_ngrok_authtoken", value: legacyToken)
            if saved {
                defaults.removeObject(forKey: "vs_remote_ngrok_authtoken")
            }
            ngrokAuthtoken = legacyToken
        } else {
            ngrokAuthtoken = KeychainHelper.load(account: "vs_ngrok_authtoken") ?? ""
        }

        // idleTimeoutMinutes defaults to 30
        idleTimeoutMinutes = defaults.object(forKey: Keys.idleTimeoutMinutes) == nil
            ? 30
            : defaults.integer(forKey: Keys.idleTimeoutMinutes)
    }
}
