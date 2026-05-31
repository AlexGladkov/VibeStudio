// MARK: - NgrokHostRef
// ARCH-H7: Extracted from RemoteControlServer.swift so the ngrok host
// reference type lives next to its single point of mutation (the server)
// without being buried at the bottom of an 800-line file.
//
// macOS 14+, Swift 5.10

import Foundation

/// Thread-safe container for the current ngrok tunnel host.
///
/// Updated on MainActor when the ngrok URL is resolved; read on NIO event loop
/// threads inside ``HTTPRequestRouter.allowedOrigin(from:)``.
///
/// Uses `NSLock` rather than an actor to avoid async overhead in the hot-path
/// CORS check (one read per HTTP request).
final class NgrokHostRef: @unchecked Sendable {
    private let lock = NSLock()
    private var _host: String?

    /// The current ngrok hostname (e.g. `"xxxx.ngrok-free.app"`), or `nil`
    /// when no tunnel is active.
    var host: String? {
        lock.lock()
        defer { lock.unlock() }
        return _host
    }

    func set(_ newHost: String?) {
        lock.lock()
        _host = newHost
        lock.unlock()
    }
}
