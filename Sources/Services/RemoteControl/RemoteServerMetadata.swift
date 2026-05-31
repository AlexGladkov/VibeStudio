// MARK: - RemoteServerMetadata
// ARCH-H8: Snapshot of Bundle.main values needed by the Remote Control
// HTTP layer. Captured on MainActor at server start so NIO event-loop
// threads never call `Bundle.main` themselves.
//
// macOS 14+, Swift 5.10

import Foundation

/// Immutable Bundle-derived metadata threaded through NIO handlers.
///
/// Initialized once on MainActor during server bootstrap — every NIO
/// callback then reads from this `Sendable` value instead of hitting
/// `Bundle.main` (which is documented main-thread-affine for many APIs).
struct RemoteServerMetadata: Sendable {
    /// Marketing version string (`CFBundleShortVersionString`).
    let appVersion: String

    /// Default fallback used when the bundle value is missing.
    static let unknownVersion = "0.0.0"

    /// Read the bundle's marketing version on MainActor.
    @MainActor
    static func current() -> RemoteServerMetadata {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
            ?? unknownVersion
        return RemoteServerMetadata(appVersion: version)
    }
}
