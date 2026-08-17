// MARK: - RemoteControlServer+Bootstrap
// Iteration 9 split. NIO bootstrap helpers extracted from
// ``RemoteControlServer`` so the primary type body stays under SwiftLint's
// `type_body_length` budget and `start()` stays under `function_body_length`.
//
// Everything here is `nonisolated` / `static` — it runs off the MainActor from
// the detached start task and never touches instance-isolated state directly
// (dependencies arrive via ``RouterRuntimeConfig`` + an explicit weak
// `serverRef`).
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOPosix
import NIOHTTP1
import NIOSSL

extension RemoteControlServer {

    /// Listen backlog for the server socket (max pending connection queue).
    private static let tcpBacklog: Int32 = 16

    /// Immutable per-connection dependencies handed to every
    /// ``HTTPRequestRouter`` a listening channel spawns.
    ///
    /// Intentionally *not* `Sendable`: the configurator factory consumes it
    /// synchronously on the MainActor and only the resulting `@Sendable`
    /// closure — capturing the destructured individual values — crosses the
    /// isolation boundary. This preserves the exact capture set of the
    /// pre-split inline implementation.
    struct RouterRuntimeConfig {
        let authService: RemoteAuthService
        let terminalService: TerminalService
        let projectManager: any ProjectManaging
        let preferences: RemoteControlPreferences
        let idleTimeoutMinutes: Int
        let staticCache: RemoteStaticFileCache
        let ngrokHostRef: NgrokHostRef
        let metadata: RemoteServerMetadata
        /// Optional — passed to `RemoteAPIHandlers` for cost reconnect recovery.
        /// Unowned reference — struct can't hold `weak`, caller must ensure lifetime.
        var costTrackerService: CostTrackerService?
    }

    /// Build the shared child-channel initializer: HTTP pipeline + router.
    ///
    /// `serverRef` arrives as a (possibly `nil`) strong snapshot; it is
    /// immediately **re-weakened** into `weakServer` so the returned closure —
    /// retained by the listening server channel for its whole lifetime — never
    /// forms a retain cycle back through `serverRef`. This mirrors the original
    /// inline `serverRef: weakSelf` weak-capture semantics byte-for-byte.
    nonisolated static func makeChildChannelConfigurator(
        config: RouterRuntimeConfig,
        serverRef: RemoteControlServer?
    ) -> @Sendable (Channel) -> EventLoopFuture<Void> {
        weak var weakServer = serverRef
        let authSvc = config.authService
        let termSvc = config.terminalService
        let projMgr = config.projectManager
        let prefs = config.preferences
        let idleTimeout = config.idleTimeoutMinutes
        let staticCache = config.staticCache
        // Weak ref to avoid the returned @Sendable closure retaining the service.
        weak var costTracker: CostTrackerService? = config.costTrackerService
        let ngrokRef = config.ngrokHostRef
        let appMetadata = config.metadata

        return { channel in
            let encoder = HTTPResponseEncoder()
            let decoder = ByteToMessageHandler(
                HTTPRequestDecoder(leftOverBytesStrategy: .forwardBytes)
            )
            let errorHandler = HTTPServerProtocolErrorHandler()
            let router = HTTPRequestRouter(
                authService: authSvc,
                terminalService: termSvc,
                projectManager: projMgr,
                preferences: prefs,
                idleTimeoutMinutes: idleTimeout,
                serverRef: weakServer,
                staticCache: staticCache,
                ngrokHostRef: ngrokRef,
                metadata: appMetadata,
                costTrackerService: costTracker
            )
            return channel.pipeline.addHandler(encoder, name: "http_encoder").flatMap {
                channel.pipeline.addHandler(decoder, name: "http_decoder")
            }.flatMap {
                channel.pipeline.addHandler(errorHandler, name: "http_error")
            }.flatMap {
                channel.pipeline.addHandler(router, name: "http_router")
            }
        }
    }

    /// The bound NIO listeners produced by ``bindChannels(configureChildChannel:bindHost:bindPort:)``.
    struct BoundListeners {
        let group: MultiThreadedEventLoopGroup
        let channel: Channel
        let httpChannel: Channel?
    }

    /// Create the event-loop group, build the TLS + plain-HTTP bootstraps and
    /// bind both listeners. The plain-HTTP listener (loopback only, port + 1)
    /// is best-effort — a failure there does not fail the whole start.
    nonisolated static func bindChannels(
        configureChildChannel: @escaping @Sendable (Channel) -> EventLoopFuture<Void>,
        bindHost: String,
        bindPort: Int
    ) async throws -> BoundListeners {
        let sslContext = try makeServerSSLContext()
        let group = MultiThreadedEventLoopGroup(numberOfThreads: 2)

        let bootstrap = makeHTTPSBootstrap(
            group: group,
            sslContext: sslContext,
            configureChildChannel: configureChildChannel
        )
        let channel = try await bootstrap.bind(host: bindHost, port: bindPort).get()

        // Plain HTTP listener on port+1 for iOS Safari (WSS + self-signed cert issue).
        // SECURITY: plain HTTP always bound to loopback only.
        let httpBootstrap = makePlainHTTPBootstrap(
            group: group,
            configureChildChannel: configureChildChannel
        )
        let httpCh = try? await httpBootstrap.bind(host: "127.0.0.1", port: bindPort + 1).get()

        return BoundListeners(group: group, channel: channel, httpChannel: httpCh)
    }

    /// Build the TLS server context (certificate + key + config).
    private nonisolated static func makeServerSSLContext() throws -> NIOSSLContext {
        // Ensure TLS certificate exists.
        let (certPath, keyPath) = try TLSCertificateManager.ensureCertificate()

        let certChain = try NIOSSLCertificate.fromPEMFile(certPath.path)
        let privateKey = try NIOSSLPrivateKey(file: keyPath.path, format: .pem)
        var tlsConfig = TLSConfiguration.makeServerConfiguration(
            certificateChain: certChain.map { .certificate($0) },
            privateKey: .privateKey(privateKey)
        )
        tlsConfig.certificateVerification = .none
        return try NIOSSLContext(configuration: tlsConfig)
    }

    /// HTTPS listener bootstrap: prepends a `NIOSSLServerHandler` before the
    /// shared HTTP pipeline configurator.
    private nonisolated static func makeHTTPSBootstrap(
        group: MultiThreadedEventLoopGroup,
        sslContext: NIOSSLContext,
        configureChildChannel: @escaping @Sendable (Channel) -> EventLoopFuture<Void>
    ) -> ServerBootstrap {
        ServerBootstrap(group: group)
            .serverChannelOption(ChannelOptions.backlog, value: Self.tcpBacklog)
            .serverChannelOption(
                ChannelOptions.socketOption(.so_reuseaddr), value: 1
            )
            .childChannelInitializer { channel in
                let sslHandler = NIOSSLServerHandler(context: sslContext)
                return channel.pipeline.addHandler(sslHandler).flatMap {
                    configureChildChannel(channel)
                }
            }
    }

    /// Plain-HTTP listener bootstrap (loopback only) using the shared HTTP
    /// pipeline configurator directly, without a TLS handler.
    private nonisolated static func makePlainHTTPBootstrap(
        group: MultiThreadedEventLoopGroup,
        configureChildChannel: @escaping @Sendable (Channel) -> EventLoopFuture<Void>
    ) -> ServerBootstrap {
        ServerBootstrap(group: group)
            .serverChannelOption(ChannelOptions.backlog, value: Self.tcpBacklog)
            .serverChannelOption(
                ChannelOptions.socketOption(.so_reuseaddr), value: 1
            )
            .childChannelInitializer(configureChildChannel)
    }
}
