// MARK: - BonjourAdvertiser
// Bonjour (DNS-SD) service advertisement for Remote Control discovery.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Advertises the Remote Control server via Bonjour (`_vibestudio._tcp`)
/// so that iOS/Android companion apps can discover the server automatically
/// on the local network.
///
/// **Lifecycle:** Owned by ``RemoteControlServer``. Publishing starts when
/// the HTTP server binds and `bonjourEnabled` is `true` in preferences.
/// Unpublished when the server stops or the user disables Bonjour.
///
/// **Security:** Bonjour only broadcasts the service name and port -- no
/// authentication tokens or PINs are exposed. Discovery alone does not
/// grant access; clients must still authenticate via PIN.
@MainActor
final class BonjourAdvertiser: NSObject, NetServiceDelegate {

    // MARK: - State

    private var service: NetService?

    /// Whether the Bonjour service is currently being published.
    private(set) var isPublishing: Bool = false

    // MARK: - Public API

    /// Start publishing the Bonjour service on the given port.
    ///
    /// - Parameters:
    ///   - name: Service name (empty string = system hostname).
    ///   - port: TCP port the HTTP/WS server is listening on.
    func publish(name: String = "", port: Int) {
        guard !isPublishing else { return }
        service = NetService(
            domain: "",
            type: "_vibestudio._tcp",
            name: name,
            port: Int32(port)
        )
        service?.delegate = self
        service?.publish()
        isPublishing = true
        Logger.remoteControl.info("Bonjour: publishing on port \(port)")
    }

    /// Stop publishing the Bonjour service.
    func unpublish() {
        service?.stop()
        service = nil
        isPublishing = false
        Logger.remoteControl.info("Bonjour: unpublished")
    }

    // MARK: - NetServiceDelegate

    nonisolated func netServiceDidPublish(_ sender: NetService) {
        Task { @MainActor in
            Logger.remoteControl.info("Bonjour: published as \(sender.name)")
        }
    }

    nonisolated func netService(
        _ sender: NetService,
        didNotPublish errorDict: [String: NSNumber]
    ) {
        Task { @MainActor [weak self] in
            Logger.remoteControl.error("Bonjour: publish failed \(errorDict)")
            self?.isPublishing = false
        }
    }
}
