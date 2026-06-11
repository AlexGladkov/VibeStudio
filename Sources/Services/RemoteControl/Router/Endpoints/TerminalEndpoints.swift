// MARK: - TerminalEndpoints
// Plain-HTTP fall-through guard for /api/v1/terminal/* and /ws/terminal/*.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for the terminal WebSocket URLs.
///
/// The live terminal stream is served via WebSocket and handled directly by
/// ``WebSocketUpgradeHandler``. This group only emits the descriptive 400 for
/// requests that hit the terminal path without an `Upgrade: websocket` header.
struct TerminalEndpoints {

    let builder: HTTPResponseBuilder

    /// Returns the standard `WebSocket upgrade required` response for a GET
    /// that reached `/api/v1/terminal/*` or `/ws/terminal/*` without the
    /// `Upgrade` header.
    func handleUpgradeRequired(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        builder.errorJSONResponse(
            status: .badRequest,
            code: "INVALID_REQUEST",
            message: "WebSocket upgrade required for terminal endpoints.",
            corsOrigin: corsOrigin,
            allocator: allocator
        )
    }
}
