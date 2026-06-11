// MARK: - AgentEndpoints
// /api/v1/assistant/* handlers (Claude / agent launch & stop).
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

/// Endpoint group for AI assistant lifecycle management.
///
/// The Claude `--dangerously-skip-permissions` policy is owned by
/// `GeneralPreferences` via the ``ClaudePermissionsResolving`` contract — this
/// endpoint never reads `UserDefaults` directly.
@MainActor
struct AgentEndpoints {

    let projectManager: any ProjectManaging
    let terminalService: TerminalService
    let claudePermissions: any ClaudePermissionsResolving
    let builder: HTTPResponseBuilder
    let decoder: JSONDecoder

    /// POST /api/v1/assistant/start
    func handleAssistantStart(
        body: ByteBuffer?,
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let projectId = projectManager.activeProjectId else {
            return builder.errorJSONResponse(
                status: .badRequest, code: "NO_ACTIVE_PROJECT",
                message: "No active project is set.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // Parse optional request body for the `assistant` field.
        let requestedAssistant: AIAssistant? = {
            guard let buf = body,
                  let bytes = buf.getBytes(at: buf.readerIndex, length: buf.readableBytes),
                  let parsed = try? decoder.decode(AssistantStartRequest.self, from: Data(bytes)) else {
                return nil
            }
            return AIAssistant(rawValue: parsed.assistant ?? "")
        }()

        let agent = requestedAssistant ?? .claude

        guard let shellSession = terminalService.sessions(for: projectId)
                .first(where: { !$0.isAgentSession }) else {
            return builder.errorJSONResponse(
                status: .serviceUnavailable, code: "NO_SHELL_SESSION",
                message: "No shell session available for the active project.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // Append `--dangerously-skip-permissions` for Claude when the policy
        // resolved by `ClaudePermissionsResolving` says so.
        var command = agent.launchCommand
        if agent == .claude && claudePermissions.claudeSkipPermissions {
            command = "claude --dangerously-skip-permissions\n"
        }
        terminalService.sendInput(command, to: shellSession.id)

        let resp = AssistantStartResponse(
            ok: true,
            assistant: agent.rawValue,
            sessionId: shellSession.id.uuidString
        )
        return builder.encodableResponse(
            status: .ok, value: resp, corsOrigin: corsOrigin, allocator: allocator
        )
    }

    /// POST /api/v1/assistant/stop
    func handleAssistantStop(
        corsOrigin: String?,
        allocator: ByteBufferAllocator
    ) -> (HTTPResponseHead, ByteBuffer)? {
        guard let projectId = projectManager.activeProjectId else {
            return builder.errorJSONResponse(
                status: .badRequest, code: "NO_ACTIVE_PROJECT",
                message: "No active project is set.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        let sessions = terminalService.sessions(for: projectId)

        // Prefer the agent session; fall back to the first available session.
        guard let targetSession = sessions.first(where: { $0.isAgentSession }) ?? sessions.first else {
            return builder.errorJSONResponse(
                status: .serviceUnavailable, code: "NO_SESSION",
                message: "No terminal session found for the active project.",
                corsOrigin: corsOrigin, allocator: allocator
            )
        }

        // Send Ctrl+C. We cannot know which assistant is running server-side
        // without additional state tracking, so we use the universal interrupt.
        terminalService.sendInput("\u{03}", to: targetSession.id)

        return builder.encodableResponse(
            status: .ok, value: OKResponse(ok: true),
            corsOrigin: corsOrigin, allocator: allocator
        )
    }
}
