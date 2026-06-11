// MARK: - DebugEndpoints
// DEBUG-only diagnostic endpoints (pin, wslog, state). macOS 14+, Swift 5.10

#if DEBUG
import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for the DEBUG-only diagnostic surface.
///
/// These endpoints are gated behind an opt-in environment variable
/// (`VS_DEBUG_API=1`) so they are never accidentally reachable in TestFlight
/// or Ad-Hoc builds distributed to external testers. Set `VS_DEBUG_API=1` in
/// the scheme's Run > Arguments > Environment Variables to enable them
/// locally.
@MainActor
struct DebugEndpoints {

    let authService: RemoteAuthService
    let builder: HTTPResponseBuilder

    /// `true` when `VS_DEBUG_API` is set in the process environment.
    static var isEnabled: Bool {
        ProcessInfo.processInfo.environment["VS_DEBUG_API"] != nil
    }

    /// Standard 404 when a debug endpoint is hit without `VS_DEBUG_API=1`.
    func handleDisabled(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        builder.errorJSONResponse(
            status: .notFound, code: "NOT_FOUND",
            message: "The requested resource was not found.",
            corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/debug/pin — expose the current PIN for automated testing.
    func handlePin(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        let pin = authService.currentPin
        let payload: [String: String] = ["pin": pin]
        let data = (try? JSONSerialization.data(withJSONObject: payload, options: []))
            ?? Data("{}".utf8)
        return builder.jsonResponse(
            status: .ok, body: data, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/debug/wslog — show the in-memory WebSocket upgrade log.
    /// Uses `JSONSerialization` (not string interpolation) to avoid manual
    /// escaping pitfalls for control characters and unicode in log lines.
    func handleWSLog(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        let log = HTTPRequestRouter.wsDebugLog
        let data = (try? JSONSerialization.data(withJSONObject: log, options: []))
            ?? Data("[]".utf8)
        return builder.jsonResponse(
            status: .ok, body: data, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/debug/state — show connected devices, tokens, IPs, and caller IP.
    func handleState(
        callerIP: String,
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer) {
        let devices: [[String: Any]] = authService.connectedDevices.map { d in
            ["id": d.id.uuidString, "ip": d.ipAddress]
        }
        let deviceCount = authService.connectedDevices.count
        let ngrok = NgrokEndpoints.snapshot(serverRef: serverRef)
        let ngrokDict: [String: Any] = [
            "running": ngrok.running,
            "url": ngrok.url as Any? ?? NSNull(),
            "error": ngrok.error as Any? ?? NSNull()
        ]
        let payload: [String: Any] = [
            "caller_ip": callerIP,
            "devices": devices,
            "device_count": deviceCount,
            "ngrok": ngrokDict
        ]
        let data = (try? JSONSerialization.data(withJSONObject: payload, options: []))
            ?? Data("{}".utf8)
        return builder.jsonResponse(
            status: .ok, body: data, corsOrigin: corsOrigin, allocator: allocator
        )
    }
}
#endif
