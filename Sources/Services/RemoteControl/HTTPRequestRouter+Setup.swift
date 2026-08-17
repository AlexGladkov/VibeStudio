// MARK: - HTTPRequestRouter+Setup
// Iteration 9 split. Collaborator wiring extracted from ``HTTPRequestRouter``'s
// initializer so `init` stays under SwiftLint's `function_body_length` budget
// and the primary type body stays under `type_body_length`. The wiring lives in
// ``Collaborators.init`` — a pure function of the injected dependencies that
// allocates the response writer, static-file server, WebSocket-upgrade
// coordinator, API router and the pre-auth sub-routers.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOHTTP1

extension HTTPRequestRouter {

    /// Immutable collaborators the router owns for its whole lifetime.
    ///
    /// The wiring lives in this value's initializer (rather than a static
    /// factory) so SwiftLint's `function_parameter_count` rule — which exempts
    /// initializers — does not flag the long dependency list.
    struct Collaborators {
        let writer: HTTPResponseWriter
        let staticFileServer: RemoteStaticFileServer
        let wsUpgrader: WebSocketUpgradeHandler
        let apiRouter: RemoteAPIRouter
        let staticRouter: StaticFileRouter
        let authRouter: AuthEndpointRouter

        /// Build every immutable collaborator in dependency order.
        init(
            authService: RemoteAuthService,
            terminalService: TerminalService,
            projectManager: any ProjectManaging,
            preferences: RemoteControlPreferences,
            serverRef: RemoteControlServer?,
            staticCache: RemoteStaticFileCache,
            metadata: RemoteServerMetadata,
            idleTimeoutMinutes: Int,
            decoder: JSONDecoder,
            isoFormatter: ISO8601DateFormatter,
            costTrackerService: CostTrackerService? = nil
        ) {
            let writer = HTTPResponseWriter(encoder: HTTPRequestRouter.makeEncoder())
            let staticFileServer = RemoteStaticFileServer(cache: staticCache, writer: writer)

            var handlers = RemoteAPIHandlers(
                authService: authService,
                terminalService: terminalService,
                projectManager: projectManager,
                preferences: preferences,
                serverRef: serverRef,
                writer: writer,
                metadata: metadata
            )
            handlers.costTrackerService = costTrackerService

            self.writer = writer
            self.staticFileServer = staticFileServer
            self.wsUpgrader = WebSocketUpgradeHandler(
                writer: writer,
                authService: authService,
                terminalService: terminalService,
                serverRef: serverRef,
                idleTimeoutMinutes: idleTimeoutMinutes
            )
            self.apiRouter = RemoteAPIRouter(
                handlers: handlers,
                decoder: decoder,
                isoFormatter: isoFormatter
            )
            // Wave-6 split: per-area sub-routers. They build their `[RouteSpec]`
            // lazily and are otherwise stateless.
            self.staticRouter = StaticFileRouter(
                staticFileServer: staticFileServer,
                writer: writer
            )
            self.authRouter = AuthEndpointRouter(
                apiHandlers: handlers,
                decoder: decoder
            )
        }
    }
}
