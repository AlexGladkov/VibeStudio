// MARK: - SessionEndpoints
// Terminal session scrollback endpoint.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for terminal session resources exposed over REST
/// (scrollback). The live terminal stream lives on the WebSocket and is
/// handled by ``WebSocketUpgradeHandler``.
@MainActor
struct SessionEndpoints {

    let terminalService: TerminalService
    let builder: HTTPResponseBuilder

    /// GET /api/v1/projects/{pid}/sessions/{sid}/scrollback
    ///
    /// Scrollback is readable by any authenticated device. Previously this
    /// required an active WS bridge (`isAttached`), which broke reconnect
    /// flows — the client needs scrollback to restore its view before the WS
    /// handshake completes. Auth is sufficient authorisation here: the session
    /// belongs to the local user who chose to share it.
    func handleScrollback(
        projectId _: UUID,
        sessionId: UUID,
        device _: RemoteDevice,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let fullContent = terminalService.rawScrollbackContent(for: sessionId) ?? ""
        let allLines = fullContent.components(separatedBy: "\n")
        // Cap returned content to last 10000 lines to prevent excessive payloads.
        let maxLines = 10_000
        let returnedLines = allLines.suffix(maxLines)
        let content = returnedLines.joined(separator: "\n")
        let resp = ScrollbackResponse(
            content: content,
            totalLines: allLines.count,
            returnedLines: returnedLines.count
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }
}
