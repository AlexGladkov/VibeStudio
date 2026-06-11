// MARK: - DiagnosticsEndpoints
// /api/v1/health and /api/v1/status handlers.
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for unauthenticated health and authenticated server status.
@MainActor
struct DiagnosticsEndpoints {

    let authService: RemoteAuthService
    let builder: HTTPResponseBuilder

    /// GET /api/v1/health — unauthenticated, includes runtime diagnostics so
    /// monitoring tools and the client UI can display uptime/capacity/version
    /// without an authenticated `/status` call.
    func handleHealth(
        serverRef: RemoteControlServer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
        let uptimeSeconds = serverRef?.uptimeSeconds ?? 0
        let connectedDevices = authService.connectedDevices.count
        let resp = HealthResponse(
            status: "healthy",
            version: version,
            uptimeSeconds: uptimeSeconds,
            connectedDevices: connectedDevices,
            maxDevices: RemoteAuthService.maxDevices,
            tls: "self-signed"
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// GET /api/v1/status — authenticated, returns server/connection/theme info.
    func handleStatus(
        serverRef: RemoteControlServer?,
        preferences: RemoteControlPreferences,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        let resp = StatusResponse(
            server: .init(
                version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0",
                apiVersion: "1.0.0",
                uptimeSeconds: serverRef?.uptimeSeconds ?? 0,
                port: preferences.remoteControlPort,
                tls: "self-signed",
                bonjourPublished: preferences.bonjourEnabled
            ),
            connections: .init(
                connectedDevices: authService.connectedDevices.count,
                maxDevices: RemoteAuthService.maxDevices,
                activeWebsockets: serverRef?.activeBridges.count ?? 0
            ),
            theme: .init(
                appearance: "dark",
                terminalColors: TerminalColorsResponse(
                    foreground: "#D4D4D4",
                    background: "#1E1E1E",
                    cursor: "#AEAFAD",
                    selection: "#264F78",
                    ansi: [
                        "#000000", "#CD3131", "#0DBC79", "#E5E510",
                        "#2472C8", "#BC3FBC", "#11A8CD", "#E5E5E5",
                        "#666666", "#F14C4C", "#23D18B", "#F5F543",
                        "#3B8EEA", "#D670D6", "#29B8DB", "#FFFFFF"
                    ]
                )
            )
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }
}
