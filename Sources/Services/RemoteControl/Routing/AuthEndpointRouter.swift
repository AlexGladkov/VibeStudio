// MARK: - AuthEndpointRouter
// Wave-6 split. Owns the public, bearer-token-free auth endpoints
// (`POST /api/v1/auth/token`). Everything that actually requires a Bearer
// token stays in the top-level router's authenticated branch because it
// shares the same MainActor token-validation + `RemoteAPIRouter` dispatch.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Builds the route table for unauthenticated auth-related endpoints.
///
/// The handler intentionally hops to `@MainActor` itself (mirroring the
/// previous inline code in ``HTTPRequestRouter``) so the public API of
/// ``RemoteAPIHandlers`` does not need to change.
struct AuthEndpointRouter {

    private let apiHandlers: RemoteAPIHandlers
    private let decoder: JSONDecoder

    init(apiHandlers: RemoteAPIHandlers, decoder: JSONDecoder) {
        self.apiHandlers = apiHandlers
        self.decoder = decoder
    }

    func routes() -> [RouteSpec] {
        let handlers = apiHandlers
        let decoder = decoder

        return [
            RouteSpec.post("/api/v1/auth/token") { head, body, ctx in
                Task { @MainActor in
                    handlers.handleAuthToken(
                        head: head,
                        body: body,
                        context: ctx,
                        decoder: decoder
                    )
                }
                return true
            }
        ]
    }
}
