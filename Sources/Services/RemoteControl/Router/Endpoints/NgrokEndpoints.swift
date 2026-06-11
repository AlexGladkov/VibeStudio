// MARK: - NgrokEndpoints
// Ngrok diagnostics surfaced via the /api/v1/status payload.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Composes the ngrok diagnostic payload embedded in `/api/v1/status` and
/// `/api/v1/debug/state`.
///
/// No standalone REST endpoint is exposed for ngrok today — tunnel lifecycle
/// is driven internally by ``NgrokTunnelService`` and surfaced through the
/// status / debug responses. This struct exists so the dispatcher can grow
/// dedicated routes (start/stop/info) without refactoring again.
struct NgrokEndpoints {

    /// Snapshot of ngrok state suitable for embedding in JSON responses.
    struct StatusSnapshot {
        let running: Bool
        let url: String?
        let error: String?
    }

    /// Build a snapshot from the live server reference.
    @MainActor
    static func snapshot(serverRef: RemoteControlServer?) -> StatusSnapshot {
        StatusSnapshot(
            running: serverRef?.isNgrokRunning ?? false,
            url: serverRef?.ngrokTunnelURL,
            error: serverRef?.ngrokError
        )
    }
}
