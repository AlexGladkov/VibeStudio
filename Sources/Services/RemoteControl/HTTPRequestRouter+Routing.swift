// MARK: - HTTPRequestRouter+Routing
// Iteration 9 split. Top-level HTTP dispatch extracted from the primary
// ``HTTPRequestRouter`` declaration to keep its body under SwiftLint's
// `type_body_length` budget. Pure routing orchestration lives here:
// per-request CORS resolution → preflight → flat sub-router lookup →
// WebSocket upgrade delegation → authenticated `/api/*` fan-out → 404.
//
// The members these methods touch (`writer`, `corsPolicy`, `preAuthRoutes`,
// `wsUpgrader`, `apiRouter`, `authService`, `remoteAddress`,
// `currentClientIP`) are declared `internal` on the main type specifically so
// this cross-file extension can reach them — behaviour is byte-for-byte
// identical to the pre-split inline implementation.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1
import OSLog

extension HTTPRequestRouter {

    // MARK: - Routing

    /// Top-level dispatcher. Wave-6 reduced this from a 250-line if-ladder
    /// (cyclomatic complexity 22) to a thin orchestrator: per-request CORS
    /// origin → CORS preflight → flat sub-router lookup → WebSocket upgrade
    /// → authenticated `/api/*` fan-out → 404.
    func routeRequest(head: HTTPRequestHead, body: ByteBuffer?, channel: Channel) {
        let path = head.uri.split(separator: "?").first.map(String.init) ?? head.uri
        let method = head.method

        logWebSocketDiagnostic(method: method, path: path, head: head)

        // ARCH-H9: resolve CORS origin once per request and pass it through
        // every send* call. Never store on the handler — keep-alive sockets
        // would otherwise risk reading the next request's origin from a
        // Task-deferred response.
        let corsOrigin = corsPolicy.allowedOrigin(from: head)

        // CORS preflight.
        if method == .OPTIONS {
            writer.sendCORSPreflight(channel: channel, corsOrigin: corsOrigin)
            return
        }

        let context = RouteRequestContext(
            channel: channel,
            corsOrigin: corsOrigin,
            clientIP: currentClientIP,
            remoteAddress: remoteAddress
        )

        // Flat dispatch over every sub-router-claimed route (static files,
        // /health, /auth/token, debug endpoints).
        for route in preAuthRoutes where route.method == method && route.pathMatcher(path) {
            if route.handler(head, body, context) { return }
        }

        // WebSocket upgrade for terminal endpoints (auth happens on first WS frame).
        if method == .GET && path.hasPrefix("/api/v1/terminal/") &&
           head.headers["Upgrade"].first?.caseInsensitiveCompare("websocket") == .orderedSame {
            wsUpgrader.handleUpgrade(
                head: head, path: path, channel: channel,
                corsOrigin: corsOrigin, clientIP: currentClientIP
            )
            return
        }

        // All other /api/ endpoints require Bearer auth.
        if path.hasPrefix("/api/") {
            dispatchAuthenticated(
                ParsedHTTPRequest(
                    head: head, body: body, path: path, method: method,
                    channel: channel, corsOrigin: corsOrigin
                )
            )
            return
        }

        writer.sendErrorJSON(
            status: .notFound,
            code: "NOT_FOUND",
            message: "The requested resource was not found.",
            channel: channel,
            corsOrigin: corsOrigin
        )
    }

    /// DEBUG-only diagnostic log entry for WS upgrade / terminal traffic.
    /// Compiled out in Release so the hot path stays branch-free there.
    private func logWebSocketDiagnostic(method: HTTPMethod, path: String, head: HTTPRequestHead) {
        #if DEBUG
        let upgradeHeader = head.headers["Upgrade"].first ?? "none"
        let connHeader = head.headers["Connection"].first ?? "none"
        if upgradeHeader != "none" || path.hasPrefix("/api/v1/terminal") {
            HTTPRequestRouter.wsLog(
                "[ROUTER] \(method.rawValue) \(path) Upgrade=\(upgradeHeader) Connection=\(connHeader) ip=\(self.remoteAddress)"
            )
        }
        #endif
    }

    /// Bearer-token validation + dispatch to ``RemoteAPIRouter``.
    ///
    /// Extracted from `routeRequest` so the top-level dispatcher stays under
    /// SwiftLint's complexity budget. Behaviour is byte-for-byte identical
    /// to the pre-Wave-6 inline branch.
    private func dispatchAuthenticated(_ request: ParsedHTTPRequest) {
        let clientIP = currentClientIP
        guard let token = RemoteAuthMiddleware.extractBearerToken(from: request.head) else {
            writer.sendErrorJSON(
                status: .unauthorized,
                code: "AUTH_REQUIRED",
                message: "Authorization header with Bearer token is required.",
                channel: request.channel,
                corsOrigin: request.corsOrigin
            )
            return
        }

        let authSvc = authService
        let apiRouter = self.apiRouter
        let theWriter = writer

        Task { @MainActor in
            let result = authSvc.validateToken(token, clientIP: clientIP)
            switch result {
            case .failure(let error):
                let authResp = RemoteAuthMiddleware.authErrorResponse(error)
                let resp = ErrorResponse(error: ErrorDetail(code: authResp.code, message: authResp.message))
                let data = (try? theWriter.encoder.encode(resp)) ?? Data()
                request.channel.eventLoop.execute {
                    theWriter.sendRawJSON(
                        status: authResp.status,
                        data: data,
                        channel: request.channel,
                        corsOrigin: request.corsOrigin,
                        retryAfterSeconds: authResp.retryAfterSeconds
                    )
                }

            case .success(let device):
                apiRouter.routeAuthenticated(request, device: device)
            }
        }
    }
}
