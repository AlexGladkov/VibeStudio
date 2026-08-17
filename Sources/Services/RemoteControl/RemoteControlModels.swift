// MARK: - RemoteControlModels
// Codable DTOs for the Remote Control REST API and WebSocket protocol.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - Authentication

/// `POST /api/v1/auth/token` request body.
struct AuthTokenRequest: Codable {
    let pin: String
}

/// `POST /api/v1/auth/token` success response.
struct AuthTokenResponseDTO: Codable {
    let token: String
    let expiresAt: Date
    let deviceId: String

    enum CodingKeys: String, CodingKey {
        case token
        case expiresAt = "expires_at"
        case deviceId = "device_id"
    }
}

/// `GET /api/v1/auth/validate` response.
struct AuthValidateResponse: Codable {
    let valid: Bool
    let deviceId: String
    let expiresAt: Date

    enum CodingKeys: String, CodingKey {
        case valid
        case deviceId = "device_id"
        case expiresAt = "expires_at"
    }
}

// MARK: - Projects & Sessions

/// Project summary in `GET /api/v1/projects` response.
struct ProjectResponse: Codable {
    let id: String
    let name: String
    let path: String
    let color: String?
    let isActive: Bool
    let git: GitInfoResponse?
    let sessions: [SessionResponse]

    enum CodingKeys: String, CodingKey {
        case id, name, path, color, git, sessions
        case isActive = "is_active"
    }
}

/// Git info embedded in a project response.
struct GitInfoResponse: Codable {
    let branch: String
    let ahead: Int
    let behind: Int
}

/// Session summary returned in project and session detail endpoints.
///
/// `totalTokens` and `estimatedCostUsd` are optional and populated only for
/// agent sessions where cost data is available. Old clients that decode this
/// struct will silently ignore the new optional fields (backward-compatible).
struct SessionResponse: Codable {
    let id: String
    let title: String
    let state: String
    let isAgent: Bool
    let hasRemoteAttachment: Bool
    let attachedDeviceId: String?
    /// Accumulated token count for the session; nil when not an agent session
    /// or no usage has been parsed yet.
    let totalTokens: Int?
    /// Estimated USD cost; nil when model pricing unknown or no data yet.
    let estimatedCostUsd: Double?

    init(
        id: String,
        title: String,
        state: String,
        isAgent: Bool,
        hasRemoteAttachment: Bool,
        attachedDeviceId: String?,
        totalTokens: Int? = nil,
        estimatedCostUsd: Double? = nil
    ) {
        self.id = id
        self.title = title
        self.state = state
        self.isAgent = isAgent
        self.hasRemoteAttachment = hasRemoteAttachment
        self.attachedDeviceId = attachedDeviceId
        self.totalTokens = totalTokens
        self.estimatedCostUsd = estimatedCostUsd
    }

    enum CodingKeys: String, CodingKey {
        case id, title, state
        case isAgent = "is_agent"
        case hasRemoteAttachment = "has_remote_attachment"
        case attachedDeviceId = "attached_device_id"
        case totalTokens = "total_tokens"
        case estimatedCostUsd = "estimated_cost_usd"
    }
}

/// `GET /api/v1/projects/{id}/sessions/{id}/scrollback` response.
struct ScrollbackResponse: Codable {
    let content: String
    let totalLines: Int
    let returnedLines: Int

    enum CodingKeys: String, CodingKey {
        case content
        case totalLines = "total_lines"
        case returnedLines = "returned_lines"
    }
}

/// Projects list wrapper for `GET /api/v1/projects`.
struct ProjectsListResponse: Codable {
    let projects: [ProjectResponse]
    let activeProjectId: String?

    enum CodingKeys: String, CodingKey {
        case projects
        case activeProjectId = "active_project_id"
    }
}

// MARK: - Recent Projects

/// Lightweight recent project info for `GET /api/v1/projects/recent`.
struct RecentProjectResponse: Codable {
    let name: String
    let path: String
    let lastOpened: String

    enum CodingKeys: String, CodingKey {
        case name, path
        case lastOpened = "last_opened"
    }
}

/// Recent projects list wrapper for `GET /api/v1/projects/recent`.
struct RecentProjectsListResponse: Codable {
    let projects: [RecentProjectResponse]
}

/// `POST /api/v1/projects/open` request body.
struct OpenProjectRequest: Codable {
    let path: String
}

/// `POST /api/v1/projects/open` success response.
struct OpenProjectResponse: Codable {
    let ok: Bool
    let projectId: String

    enum CodingKeys: String, CodingKey {
        case ok
        case projectId = "project_id"
    }
}

// MARK: - Devices

/// Device info in `GET /api/v1/devices` response.
///
/// `ip` is populated only when `isSelf == true`. Other devices' IPs are
/// withheld to avoid disclosing peer network locations to authenticated
/// (but possibly compromised) clients (SEC-M3).
struct DeviceResponse: Codable {
    let deviceId: String
    let ip: String?
    let userAgent: String
    let connectedSince: Date
    let lastActivity: Date
    let attachedSessions: [String]
    let isSelf: Bool

    enum CodingKeys: String, CodingKey {
        case ip
        case deviceId = "device_id"
        case userAgent = "user_agent"
        case connectedSince = "connected_since"
        case lastActivity = "last_activity"
        case attachedSessions = "attached_sessions"
        case isSelf = "is_self"
    }
}

/// Devices list wrapper for `GET /api/v1/devices`.
struct DevicesListResponse: Codable {
    let devices: [DeviceResponse]
    let maxDevices: Int

    enum CodingKeys: String, CodingKey {
        case devices
        case maxDevices = "max_devices"
    }
}

// MARK: - Server Status

/// `GET /api/v1/status` response.
///
/// SEC-M1: intentionally minimal. Previously this endpoint exposed
/// `uptime`, `port`, `tls` mode, and the full terminal color palette —
/// all useful for fingerprinting a deployment from a leaked authenticated
/// token. Now returns only the version + live connection counts.
struct StatusResponse: Codable {
    let version: String
    let connectedDevices: Int
    let activeWebsockets: Int

    enum CodingKeys: String, CodingKey {
        case version
        case connectedDevices = "connected_devices"
        case activeWebsockets = "active_websockets"
    }
}

/// Terminal color scheme used in status and theme_changed WebSocket messages.
struct TerminalColorsResponse: Codable {
    let foreground: String
    let background: String
    let cursor: String
    let selection: String
    let ansi: [String]
}

// MARK: - Project Activation

/// `POST /api/v1/projects/:id/activate` success response.
struct OKResponse: Codable {
    let ok: Bool
}

// MARK: - Assistant Control

/// `POST /api/v1/assistant/start` optional request body.
struct AssistantStartRequest: Codable {
    /// Raw value of `AIAssistant` (e.g. `"claude"`, `"opencode"`). Defaults to `"claude"` when absent.
    let assistant: String?
}

/// `POST /api/v1/assistant/start` success response.
struct AssistantStartResponse: Codable {
    let ok: Bool
    /// The resolved assistant that was launched.
    let assistant: String
    /// The shell session ID that received the launch command.
    let sessionId: String

    enum CodingKeys: String, CodingKey {
        case ok, assistant
        case sessionId = "session_id"
    }
}

// MARK: - Error Responses

/// Standard error envelope for all API error responses.
struct ErrorResponse: Codable {
    let error: ErrorDetail
}

/// Machine-readable error detail.
struct ErrorDetail: Codable {
    let code: String
    let message: String
    let details: [String: AnyCodableValue]?

    init(code: String, message: String, details: [String: AnyCodableValue]? = nil) {
        self.code = code
        self.message = message
        self.details = details
    }
}

/// Type-erased Codable value for error `details` dictionary.
///
/// Supports `String`, `Int`, `Bool`, and `Double` -- the common types
/// used in error detail payloads.
enum AnyCodableValue: Codable, Equatable {
    case string(String)
    case int(Int)
    case bool(Bool)
    case double(Double)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let v = try? container.decode(Bool.self) { self = .bool(v); return }
        if let v = try? container.decode(Int.self) { self = .int(v); return }
        if let v = try? container.decode(Double.self) { self = .double(v); return }
        if let v = try? container.decode(String.self) { self = .string(v); return }
        throw DecodingError.typeMismatch(
            AnyCodableValue.self,
            .init(codingPath: decoder.codingPath, debugDescription: "Unsupported value type")
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let v): try container.encode(v)
        case .int(let v): try container.encode(v)
        case .bool(let v): try container.encode(v)
        case .double(let v): try container.encode(v)
        }
    }
}

// MARK: - WebSocket Messages (Client -> Server)

/// Discriminator for client-to-server WebSocket messages.
enum WSMessageType: String, Codable {
    case auth
    case input
    case resize
    case ping
    case detach
}

/// Client-to-server: authentication (must be first message after WS connect).
struct WSAuthMessage: Codable {
    let type: WSMessageType
    let token: String
}

/// Client-to-server: terminal text input.
struct WSInputMessage: Codable {
    let type: WSMessageType
    let data: String
}

/// Client-to-server: terminal resize.
struct WSResizeMessage: Codable {
    let type: WSMessageType
    let cols: Int
    let rows: Int
}

/// Client-to-server: keepalive ping.
struct WSPingMessage: Codable {
    let type: WSMessageType
    let ts: Int64
}

// MARK: - WebSocket Messages (Server -> Client)

/// Server-to-client: session state change.
struct WSSessionStateMessage: Codable {
    let type: String
    let sessionId: String
    let state: String
    let exitCode: Int32?

    enum CodingKeys: String, CodingKey {
        case type, state
        case sessionId = "session_id"
        case exitCode = "exit_code"
    }
}

/// Server-to-client: keepalive pong.
struct WSPongMessage: Codable {
    let type: String
    let ts: Int64
    let serverTs: Int64

    enum CodingKeys: String, CodingKey {
        case type, ts
        case serverTs = "server_ts"
    }
}

/// Server-to-client: theme update.
struct WSThemeChangedMessage: Codable {
    let type: String
    let appearance: String
    let terminalColors: TerminalColorsResponse

    enum CodingKeys: String, CodingKey {
        case type, appearance
        case terminalColors = "terminal_colors"
    }
}

/// Server-to-client: session list update for a project.
struct WSSessionsChangedMessage: Codable {
    let type: String
    let projectId: String
    let sessions: [SessionResponse]

    enum CodingKeys: String, CodingKey {
        case type, sessions
        case projectId = "project_id"
    }
}

/// Server-to-client: a peer device was disconnected.
struct WSDeviceDisconnectedMessage: Codable {
    let type: String
    let deviceId: String
    let reason: String

    enum CodingKeys: String, CodingKey {
        case type, reason
        case deviceId = "device_id"
    }
}

/// Server-to-client: protocol-level error.
struct WSErrorMessage: Codable {
    let type: String
    let code: String
    let message: String
    let fatal: Bool
}

/// Server-to-client: rate limit warning.
struct WSRateLimitedMessage: Codable {
    let type: String
    let message: String
    let retryAfterMs: Int

    enum CodingKeys: String, CodingKey {
        case type, message
        case retryAfterMs = "retry_after_ms"
    }
}

/// Server-to-client: agent session cost update.
///
/// Sent to mobile clients so they can display real-time token/cost counters
/// without polling. Addressed to bridges whose `sessionId` matches.
///
/// **Protocol contract:** Clients MUST silently ignore unknown `type` values
/// for forward compatibility (old clients receiving this message must not crash).
///
/// `estimatedCostUsd` is `nil` when the model is not in the local pricing table.
/// Clients should display `$?` rather than `$0.00` in that case.
struct WSCostUpdateMessage: Codable {
    let type: String
    let sessionId: String
    let projectId: String
    let promptTokens: Int
    let completionTokens: Int
    let totalTokens: Int
    /// Estimated USD cost; nil when model pricing is unknown.
    let estimatedCostUsd: Double?
    /// Agent model string, if parseable from CLI output.
    let model: String?

    enum CodingKeys: String, CodingKey {
        case type, model
        case sessionId = "session_id"
        case projectId = "project_id"
        case promptTokens = "prompt_tokens"
        case completionTokens = "completion_tokens"
        case totalTokens = "total_tokens"
        case estimatedCostUsd = "estimated_cost_usd"
    }
}
