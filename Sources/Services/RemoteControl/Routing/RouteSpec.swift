// MARK: - RouteSpec
// Wave-6 split. Lightweight value type used by the sub-routers to describe a
// single HTTP route (method + path predicate + handler). The handler is given
// the request + a per-request ``RequestContext`` containing the channel and
// already-resolved CORS origin / client IP. Sub-routers expose
// ``routes() -> [RouteSpec]`` so ``HTTPRequestRouter.routeRequest`` becomes a
// flat dispatch loop instead of a 250-line if-ladder.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Per-request data passed to every ``RouteSpec`` handler.
///
/// Captures the values that depend on the calling NIO channel (CORS origin,
/// real TCP client IP). They are computed once at the top of
/// ``HTTPRequestRouter.routeRequest`` and threaded through every match so
/// keep-alive sockets never see another request's context.
struct RouteRequestContext {

    /// NIO channel to write the response back on.
    let channel: Channel

    /// Already-validated CORS origin (`nil` when none allowed).
    let corsOrigin: String?

    /// Real TCP client IP captured at `channelActive` time. Never derived
    /// from spoofable headers — safe for rate-limiting / token binding.
    let clientIP: String

    /// Same as ``clientIP``; kept as a separate property for endpoints
    /// (currently `handleAuthToken`) whose signature distinguishes between
    /// the two for legacy logging reasons.
    let remoteAddress: String
}

/// Fully-parsed HTTP request threaded through the authenticated dispatch chain
/// (`HTTPRequestRouter.dispatchAuthenticated` → `RemoteAPIRouter.routeAuthenticated`).
///
/// Bundles the request essentials that both stages need so neither method has
/// to take a 6+/7-argument parameter list. All members are `Sendable`, so the
/// value can cross into the token-validation `Task { @MainActor }` unchanged.
struct ParsedHTTPRequest {

    /// Original request head (headers, method, URI).
    let head: HTTPRequestHead

    /// Accumulated request body, if any.
    let body: ByteBuffer?

    /// Request path with the query string stripped.
    let path: String

    /// HTTP method.
    let method: HTTPMethod

    /// NIO channel to write the response back on.
    let channel: Channel

    /// Resolved CORS origin (`nil` when none allowed).
    let corsOrigin: String?
}

/// Declarative description of an HTTP route handled by a sub-router.
///
/// Sub-routers build their `[RouteSpec]` once (lazy or cached) and hand it to
/// the top-level ``HTTPRequestRouter`` which walks the combined list looking
/// for the first method + path match.
///
/// **Why a struct of closures rather than a protocol-with-method approach?**
/// Each sub-router needs to inject a different set of dependencies into its
/// handlers; closures let us capture those at `routes()` build time without
/// inventing a per-endpoint type hierarchy. Sub-routers themselves remain
/// trivially testable (each `routes()` returns a deterministic list).
struct RouteSpec {

    /// HTTP method this route reacts to.
    let method: HTTPMethod

    /// Returns `true` when this route should handle the given URI path.
    ///
    /// The router never feeds the query string in — `path` is already
    /// stripped down to the URI path component.
    let pathMatcher: @Sendable (String) -> Bool

    /// Endpoint implementation. The router invokes it after a successful
    /// method + path match. Returns `true` when the handler claimed the
    /// request (always the case today; the boolean reserves room for a
    /// future "no, fall through to the next route" behaviour without
    /// changing the call site).
    let handler: (HTTPRequestHead, ByteBuffer?, RouteRequestContext) -> Bool

    // MARK: - Convenience factories

    /// Exact-path GET route.
    static func get(
        _ path: String,
        handler: @escaping (HTTPRequestHead, ByteBuffer?, RouteRequestContext) -> Bool
    ) -> RouteSpec {
        RouteSpec(
            method: .GET,
            pathMatcher: { $0 == path },
            handler: handler
        )
    }

    /// Exact-path POST route.
    static func post(
        _ path: String,
        handler: @escaping (HTTPRequestHead, ByteBuffer?, RouteRequestContext) -> Bool
    ) -> RouteSpec {
        RouteSpec(
            method: .POST,
            pathMatcher: { $0 == path },
            handler: handler
        )
    }

    /// Prefix-match GET route. Useful for `/vendor/...` style wildcards.
    static func getPrefix(
        _ prefix: String,
        handler: @escaping (HTTPRequestHead, ByteBuffer?, RouteRequestContext) -> Bool
    ) -> RouteSpec {
        RouteSpec(
            method: .GET,
            pathMatcher: { $0.hasPrefix(prefix) },
            handler: handler
        )
    }
}
