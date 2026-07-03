// MARK: - StaticFileRouter
// Wave-6 split. Owns every static-asset endpoint served by
// ``RemoteControlServer``: the four well-known top-level files (`/`,
// `/app.css`, `/app.js`, `/favicon.ico`), the `/vendor/*` wildcard and the
// unauthenticated `/api/v1/health` probe. Each entry is exposed as a
// ``RouteSpec`` so the top-level router only needs to walk a flat list.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Builds the route table for static files + the unauthenticated health
/// probe. Holds an ``RemoteStaticFileServer`` and a writer reference so
/// `routes()` can capture them in the handler closures.
struct StaticFileRouter {

    private let staticFileServer: RemoteStaticFileServer
    private let writer: HTTPResponseWriter

    init(staticFileServer: RemoteStaticFileServer, writer: HTTPResponseWriter) {
        self.staticFileServer = staticFileServer
        self.writer = writer
    }

    /// All routes this sub-router claims.
    ///
    /// Built fresh on each call — the cost is a few struct allocations and
    /// keeps the sub-router itself stateless after init. The top-level
    /// router caches the array via `lazy var` so this only runs once per
    /// channel handler.
    func routes() -> [RouteSpec] {
        // Top-level web UI assets + the /vendor wildcard + health probe.
        return [
            staticAssetRoute("/", file: "index.html", contentType: "text/html"),
            staticAssetRoute("/app.css", file: "app.css", contentType: "text/css"),
            staticAssetRoute("/app.js", file: "app.js", contentType: "application/javascript"),
            staticAssetRoute("/favicon.ico", file: "favicon.ico", contentType: "image/x-icon"),
            vendorRoute(),
            healthRoute()
        ]
    }

    /// Exact-path GET route that serves a single well-known static asset.
    private func staticAssetRoute(_ path: String, file: String, contentType: String) -> RouteSpec {
        let server = staticFileServer
        return RouteSpec.get(path) { _, _, ctx in
            server.serveStaticFile(
                file, contentType: contentType,
                channel: ctx.channel, corsOrigin: ctx.corsOrigin
            )
            return true
        }
    }

    /// `/vendor/<file>` wildcard (xterm.js, addons, etc.) with MIME sniffing
    /// from the file extension.
    private func vendorRoute() -> RouteSpec {
        let server = staticFileServer
        return RouteSpec.getPrefix("/vendor/") { head, _, ctx in
            let path = head.uri.split(separator: "?").first.map(String.init) ?? head.uri
            let fileName = String(path.dropFirst(1)) // "vendor/..."
            let ext = (fileName as NSString).pathExtension
            let mime = MIMETypeResolver.mimeType(for: ext)
            server.serveStaticFile(
                fileName, contentType: mime,
                channel: ctx.channel, corsOrigin: ctx.corsOrigin
            )
            return true
        }
    }

    /// Unauthenticated health probe. Kept in the static-file router
    /// because it shares the "no bearer required" property and a
    /// tiny constant payload — wiring it through the auth pipeline
    /// would only add overhead.
    private func healthRoute() -> RouteSpec {
        let writer = writer
        return RouteSpec.get("/api/v1/health") { _, _, ctx in
            let channel = ctx.channel
            let corsOrigin = ctx.corsOrigin
            let theWriter = writer
            Task { @MainActor in
                theWriter.sendRawJSON(
                    status: .ok,
                    data: Data(#"{"status":"healthy"}"#.utf8),
                    channel: channel,
                    corsOrigin: corsOrigin
                )
            }
            return true
        }
    }
}
