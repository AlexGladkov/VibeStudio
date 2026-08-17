// MARK: - RemoteAPIHandlers
// ARCH-H1: All `handle*` endpoint logic extracted from HTTPRequestRouter.
// Pure value type pinned to `@MainActor` — no instance state beyond the
// services + writer injected at construction. The router calls into these
// methods after resolving the route + decoding the bearer token.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// MainActor-pinned dispatcher that implements every REST endpoint of the
/// Remote Control API.
///
/// **Lifecycle:** One short-lived instance per request (cheap struct copy).
/// All services it holds are reference types or `@MainActor`-bound and are
/// already in scope on the calling event loop.
@MainActor
struct RemoteAPIHandlers {

    // MARK: - Dependencies

    let authService: RemoteAuthService
    let terminalService: TerminalService
    let projectManager: any ProjectManaging
    let preferences: RemoteControlPreferences
    weak var serverRef: RemoteControlServer?
    let writer: HTTPResponseWriter
    let metadata: RemoteServerMetadata
    /// Optional — nil if cost tracker disabled at app level.
    weak var costTrackerService: CostTrackerService?

    // NOTE: the remaining endpoints live in `RemoteAPIHandlers+Auth`,
    // `+Projects`, `+Devices` and `+Sessions` (Iteration 9 split).
}
