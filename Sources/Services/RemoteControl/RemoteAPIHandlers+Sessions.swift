// MARK: - RemoteAPIHandlers+Sessions
// Iteration 9 split. Session-scoped endpoints (scrollback + AI assistant
// start/stop) extracted from ``RemoteAPIHandlers`` to keep the primary type
// body under SwiftLint's `type_body_length` budget. Behaviour unchanged.
//
// macOS 14+, Swift 5.10

import Foundation
import NIOCore
import NIOHTTP1

extension RemoteAPIHandlers {

    // MARK: - Scrollback

    func handleScrollback(
        projectId: UUID,
        sessionId: UUID,
        device: RemoteDevice,
        channel: Channel,
        corsOrigin: String?
    ) {
        // SEC-C3: BOLA prevention. Deny scrollback when another device owns the
        // WS bridge for this session — that buffer may contain that device's
        // secrets. Allow when no bridge exists (initial / reconnect window).
        // SEC-2: fail closed if the server reference is gone (shutdown race) —
        // without it we cannot verify bridge ownership, so we must not serve
        // any buffer rather than skip the check.
        guard let server = serverRef else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "SERVER_UNAVAILABLE",
                    message: "Server is shutting down."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .serviceUnavailable, channel: channel, corsOrigin: corsOrigin
            )
            return
        }
        // P0-3 fix (BOLA/IDOR): always verify the session's `projectId` against
        // the one supplied in the URL BEFORE checking bridge ownership. When no
        // bridge is attached the old foreign-owner check passed, so any device
        // could read scrollback of another project by guessing a sessionId.
        // 404 (not 403) on mismatch to avoid leaking unrelated-session existence.
        guard let sessionProjectId = terminalService.session(for: sessionId)?.projectId,
              sessionProjectId == projectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "SESSION_NOT_FOUND",
                    message: "Session not found for the given project."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .notFound, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // P1-7: O(1) foreign-owner check via the secondary session index
        // (replaces the previous O(n) `activeBridges.values.first { ... }` scan).
        let attachedBridge = server.bridgeRegistry.bridge(forSession: sessionId)
        let hasForeignOwner = attachedBridge.map { $0.deviceId != device.id } ?? false
        if hasForeignOwner {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "SESSION_NOT_OWNED",
                    message: "Session is attached to another device."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .forbidden, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        let fullContent = terminalService.rawScrollbackContent(for: sessionId) ?? ""
        let allLines = fullContent.components(separatedBy: "\n")
        let maxLines = 10_000
        let returnedLines = allLines.suffix(maxLines)
        let content = returnedLines.joined(separator: "\n")
        let resp = ScrollbackResponse(
            content: content,
            totalLines: allLines.count,
            returnedLines: returnedLines.count
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    // MARK: - AI Assistant

    func handleAssistantStart(
        body: ByteBuffer?,
        channel: Channel,
        corsOrigin: String?,
        decoder: JSONDecoder
    ) {
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // Parse optional `assistant` field; default to .claude.
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
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_SHELL_SESSION",
                    message: "No shell session available for the active project."
                )
            )
            // API-4: absence of a shell session is a domain state, not a
            // transient overload — 404, not 503 (clients must not auto-retry).
            writer.sendEncodableResponse(
                resp, status: .notFound, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // SEC-C2: Remote Control clients never receive --dangerously-skip-permissions
        // regardless of the local preference. The flag is intentional only for direct
        // local invocation — over-the-wire callers (incl. ngrok) must go through
        // Claude's standard permission prompts.
        let command = agent.launchCommand(skipPermissions: false)
        terminalService.sendInput(command, to: shellSession.id)

        let resp = AssistantStartResponse(
            ok: true,
            assistant: agent.rawValue,
            sessionId: shellSession.id.uuidString
        )
        writer.sendEncodableResponse(resp, status: .ok, channel: channel, corsOrigin: corsOrigin)
    }

    func handleAssistantStop(channel: Channel, corsOrigin: String?) {
        guard let projectId = projectManager.activeProjectId else {
            let resp = ErrorResponse(
                error: ErrorDetail(code: "NO_ACTIVE_PROJECT", message: "No active project is set.")
            )
            writer.sendEncodableResponse(
                resp, status: .badRequest, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        let sessions = terminalService.sessions(for: projectId)
        // API-5: only interrupt an *agent* session. The previous
        // `?? sessions.first` fallback sent Ctrl+C into whatever session
        // existed — including a plain user shell — aborting the user's own
        // running command. With no agent session there is nothing to stop.
        guard let targetSession = sessions.first(where: { $0.isAgentSession }) else {
            let resp = ErrorResponse(
                error: ErrorDetail(
                    code: "NO_AGENT_SESSION",
                    message: "No agent session running for the active project."
                )
            )
            writer.sendEncodableResponse(
                resp, status: .notFound, channel: channel, corsOrigin: corsOrigin
            )
            return
        }

        // We cannot know which assistant is running server-side without extra
        // state tracking; default to the universal Ctrl+C interrupt.
        terminalService.sendInput("\u{03}", to: targetSession.id)
        writer.sendEncodableResponse(
            OKResponse(ok: true), status: .ok, channel: channel, corsOrigin: corsOrigin
        )
    }
}
