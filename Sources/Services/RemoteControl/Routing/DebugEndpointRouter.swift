// MARK: - DebugEndpointRouter
// Wave-6 split. Bundles the three `/api/v1/debug/*` endpoints that are
// compiled in only for DEBUG builds AND only enabled when the binary was
// loaded from a developer build directory (SEC-M1 — see
// `HTTPRequestRouter.isDeveloperBuild`). When SEC-M1 evaluates to `false`
// every debug route responds with a 404 indistinguishable from the
// catch-all so a developer-build .app re-signed for TestFlight cannot leak
// the PIN over the network.
//
// macOS 14+, Swift 5.10

#if DEBUG

import Foundation
import NIOCore
import NIOHTTP1

/// Builds the route table for `/api/v1/debug/*` endpoints.
///
/// The "is this enabled?" decision is encoded inside the closure so the
/// 404 fallback travels through the same `RouteSpec` machinery as a real
/// hit — no separate code path that could drift out of sync.
struct DebugEndpointRouter {

    private let authService: RemoteAuthService
    private weak var serverRef: RemoteControlServer?
    private let writer: HTTPResponseWriter
    private let isEnabled: Bool

    init(
        authService: RemoteAuthService,
        serverRef: RemoteControlServer?,
        writer: HTTPResponseWriter,
        isEnabled: Bool
    ) {
        self.authService = authService
        self.serverRef = serverRef
        self.writer = writer
        self.isEnabled = isEnabled
    }

    func routes() -> [RouteSpec] {
        return [pinRoute(), wslogRoute(), stateRoute()]
    }

    /// `GET /api/v1/debug/pin` — returns the current auth PIN (loopback + dev
    /// build only; otherwise a 404 indistinguishable from the catch-all).
    private func pinRoute() -> RouteSpec {
        let writer = writer
        let isEnabled = isEnabled
        let authSvc = authService
        return RouteSpec.get("/api/v1/debug/pin") { _, _, ctx in
            guard isEnabled, LoopbackCheck.isLoopback(ctx.clientIP) else {
                Self.sendDisabled(writer: writer, ctx: ctx)
                return true
            }
            let channel = ctx.channel
            let corsOrigin = ctx.corsOrigin
            Task { @MainActor in
                let pin = authSvc.currentPin
                let json = "{\"pin\":\"\(pin)\"}"
                channel.eventLoop.execute {
                    writer.sendRawJSON(
                        status: .ok, data: Data(json.utf8),
                        channel: channel, corsOrigin: corsOrigin
                    )
                }
            }
            return true
        }
    }

    /// `GET /api/v1/debug/wslog` — dumps the ring buffer of WS diagnostics.
    private func wslogRoute() -> RouteSpec {
        let writer = writer
        let isEnabled = isEnabled
        return RouteSpec.get("/api/v1/debug/wslog") { _, _, ctx in
            guard isEnabled, LoopbackCheck.isLoopback(ctx.clientIP) else {
                Self.sendDisabled(writer: writer, ctx: ctx)
                return true
            }
            let log = HTTPRequestRouter.wsDebugLog
            let data = (try? JSONSerialization.data(withJSONObject: log)) ?? Data("[]".utf8)
            writer.sendRawJSON(
                status: .ok, data: data,
                channel: ctx.channel, corsOrigin: ctx.corsOrigin
            )
            return true
        }
    }

    /// `GET /api/v1/debug/state` — connected devices + ngrok status snapshot.
    private func stateRoute() -> RouteSpec {
        let writer = writer
        let isEnabled = isEnabled
        let authSvc = authService
        weak var serverRefLocal = serverRef
        return RouteSpec.get("/api/v1/debug/state") { _, _, ctx in
            guard isEnabled, LoopbackCheck.isLoopback(ctx.clientIP) else {
                Self.sendDisabled(writer: writer, ctx: ctx)
                return true
            }
            let channel = ctx.channel
            let corsOrigin = ctx.corsOrigin
            let callerIP = ctx.clientIP
            Task { @MainActor in
                let devices = authSvc.connectedDevices.map { dev in
                    ["id": dev.id, "ip": dev.ipAddress]
                }
                let payload: [String: Any] = [
                    "caller_ip": callerIP,
                    "devices": devices,
                    "device_count": authSvc.connectedDevices.count,
                    "ngrok": [
                        "running": serverRefLocal?.isNgrokRunning ?? false,
                        "url": serverRefLocal?.ngrokTunnelURL ?? "null",
                        "error": serverRefLocal?.ngrokError ?? "null"
                    ]
                ]
                let data = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data("{}".utf8)
                channel.eventLoop.execute {
                    writer.sendRawJSON(
                        status: .ok, data: data,
                        channel: channel, corsOrigin: corsOrigin
                    )
                }
            }
            return true
        }
    }

    // MARK: - Private

    private static func sendDisabled(writer: HTTPResponseWriter, ctx: RouteRequestContext) {
        writer.sendErrorJSON(
            status: .notFound, code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: ctx.channel, corsOrigin: ctx.corsOrigin
        )
    }
}

#endif
