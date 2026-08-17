// MARK: - RemoteSessionBridge+Control
// Quick-Action control command handlers (kill / pause / rerun / clear).
// Extracted from RemoteSessionBridge to keep the primary type body under
// SwiftLint's `type_body_length` budget. Behaviour is identical to what would
// live inline in the main type.
//
// Protocol:
//   - Each command sends back a WSControlAckMessage (ok=true/false).
//   - sessionId is taken from self.sessionId (BOLA protection — E1/E2).
//   - Authorization gated by bridge ownership (bridge only exists for owner).
//   - Separate rate-limit quota (10/min), independent of PTY-input 120/min.
//   - Every command resets the idle timer (DoD #12).
//   - Audit logged via RemoteAuditLog (DoD #13, #14).
//
// macOS 14+, Swift 5.10

import Foundation
import OSLog

extension RemoteSessionBridge {

    // MARK: - Control Rate Limit Guard

    /// Check and update the separate control-channel rate limit.
    ///
    /// Returns `true` if the command may proceed; sends a `control_ack`
    /// rate_limited failure and returns `false` if the quota is exceeded.
    ///
    /// The control channel has its own quota (10/min) that is independent
    /// of the PTY-input quota (120/min) so that burst input cannot eat
    /// control budget and vice versa (DoD #8).
    private func checkControlRateLimit(action: String) -> Bool {
        let now = Date()
        if now.timeIntervalSince(controlRateLimitWindowStart) >= RemoteSessionBridge.rateLimitWindowSeconds {
            controlRateLimitWindowStart = now
            controlRateLimitCount = 0
        }

        if controlRateLimitCount >= RemoteSessionBridge.maxControlPerMinute {
            sendControlAck(.failure(
                action: action,
                reason: "Control rate limit exceeded. Slow down.",
                code: "rate_limited",
                retryAfterMs: RemoteSessionBridge.controlRateLimitRetryAfterMs
            ))
            return false
        }

        controlRateLimitCount += 1
        return true
    }

    // MARK: - Kill

    /// Handle a `kill` control command from the authenticated device.
    ///
    /// - `force == false`: sends SIGINT (Ctrl+C) via PTY bytes — reversible,
    ///   suitable for a single tap.
    /// - `force == true`: escalates to SIGTERM → SIGKILL cascade via
    ///   `TerminalService.killSession(force:true)` — irreversible, long-press path.
    ///   Logged at `.warning` via `RemoteAuditLog.forceKill`.
    ///
    /// Idempotent on already-exited sessions: session not found → no-op, ack ok=true (E5).
    func handleKill(force: Bool) {
        resetIdleTimer()
        guard checkControlRateLimit(action: "kill") else { return }

        RemoteAuditLog.controlAction(action: force ? "kill_force" : "kill", deviceId: deviceId, sessionId: sessionId)

        guard terminalService.session(for: sessionId) != nil else {
            // E5: process already exited — idempotent no-op, send ok=true.
            sendControlAck(.success(action: "kill"))
            return
        }

        if force {
            // Irreversible escalation path (long-press + confirmation on client).
            RemoteAuditLog.forceKill(deviceId: deviceId, sessionId: sessionId)
            terminalService.killSession(sessionId, force: true)
        } else {
            // Soft kill: SIGINT via Ctrl+C bytes into PTY.
            // Ctrl+C (ETX = 0x03) is processed by the terminal line discipline
            // which delivers SIGINT to the foreground process group — same as
            // a user pressing Ctrl+C. More correct than direct kill(pid, SIGINT).
            terminalService.sendInput("\u{03}", to: sessionId)
        }

        sendControlAck(.success(action: "kill"))
    }

    // MARK: - Pause

    /// Handle a `pause` control command.
    ///
    /// Delivers Ctrl+Z (0x1A) to the PTY. This causes the terminal line
    /// discipline to deliver SIGTSTP to the foreground process group,
    /// suspending the current job (e.g. the agent). The shell regains
    /// control and prints `[1]+ Stopped ...`.
    ///
    /// **Why Ctrl+Z and not direct SIGTSTP?**
    /// Sending SIGTSTP directly to the shell-leader PID would suspend the
    /// shell itself, not the foreground job. The Ctrl+Z byte routed through
    /// the line discipline correctly targets the entire foreground pgroup.
    ///
    /// If the foreground process ignores SIGTSTP (E8) the byte is still
    /// delivered (ok=true) — the client should not display "paused" unless
    /// confirmed by a follow-up state change message.
    func handlePause() {
        resetIdleTimer()
        guard checkControlRateLimit(action: "pause") else { return }

        RemoteAuditLog.controlAction(action: "pause", deviceId: deviceId, sessionId: sessionId)

        guard terminalService.session(for: sessionId) != nil else {
            sendControlAck(.failure(action: "pause", reason: "Session not found.", code: "SESSION_NOT_FOUND"))
            return
        }

        // Ctrl+Z → ASCII 0x1A (SUB) → terminal line discipline → SIGTSTP to pgroup.
        terminalService.sendInput("\u{1A}", to: sessionId)
        sendControlAck(.success(action: "pause"))
    }

    // MARK: - Rerun

    /// Handle a `rerun` control command.
    ///
    /// Only applicable to agent sessions (`isAgentSession == true`). The
    /// current agent process is force-killed and restarted with the same
    /// agent kind and working directory stored server-side. The API key is
    /// resolved from Keychain — NEVER from the WS payload (SEC-C2, DoD #7).
    ///
    /// Failures:
    /// - Not an agent session → `NO_AGENT_SESSION` (E3, consistent with API-5).
    /// - No stored agentKind → `NO_STORED_COMMAND` (E9).
    /// - Relaunch fails (binary not found, session limit) → `RERUN_FAILED` (E13).
    func handleRerun(apiKeyResolver: KeychainAPIKeyResolver) {
        resetIdleTimer()
        guard checkControlRateLimit(action: "rerun") else { return }

        RemoteAuditLog.controlAction(action: "rerun", deviceId: deviceId, sessionId: sessionId)

        guard let existing = terminalService.session(for: sessionId) else {
            sendControlAck(.failure(action: "rerun", reason: "Session not found.", code: "SESSION_NOT_FOUND"))
            return
        }

        guard existing.isAgentSession else {
            // E3: Re-run on shell sessions is explicitly rejected (mirrors API-5).
            sendControlAck(.failure(
                action: "rerun",
                reason: "Re-run is only available for agent sessions.",
                code: "NO_AGENT_SESSION"
            ))
            return
        }

        guard existing.agentKind != nil else {
            // E9: agent kind not stored server-side — cannot rerun without knowing what to launch.
            sendControlAck(.failure(
                action: "rerun",
                reason: "No stored command for this session. Cannot re-run.",
                code: "NO_STORED_COMMAND"
            ))
            return
        }

        let newSession = terminalService.rerunAgentSession(sessionId) { agent in
            // SEC-C2: API key from Keychain only — NEVER from WS payload.
            guard let envVar = agent.apiKeyEnvironmentVariable else { return nil }
            return apiKeyResolver.resolve(for: envVar)
        }

        if newSession != nil {
            sendControlAck(.success(action: "rerun"))
        } else {
            // E13: relaunch failed (binary missing, session limit, etc.)
            sendControlAck(.failure(
                action: "rerun",
                reason: "Failed to relaunch agent session.",
                code: "RERUN_FAILED"
            ))
        }
    }

    // MARK: - Clear

    /// Handle a `clear` control command.
    ///
    /// Resets the terminal scrollback buffer server-side. The ack with
    /// `action=clear` is the signal for the client to clear its local
    /// xterm buffer (single source of truth = server, DoD #6).
    ///
    /// E14: view absent → failure ack (no-op, no crash).
    /// E7: clear during active output — new output continues after clear.
    func handleClear() {
        resetIdleTimer()
        guard checkControlRateLimit(action: "clear") else { return }

        RemoteAuditLog.controlAction(action: "clear", deviceId: deviceId, sessionId: sessionId)

        let ok = terminalService.clearScrollback(for: sessionId)
        if ok {
            sendControlAck(.success(action: "clear"))
        } else {
            // E14: view not found / session gone.
            sendControlAck(.failure(
                action: "clear",
                reason: "Session view not available for clearing.",
                code: "CLEAR_FAILED"
            ))
        }
    }

    // MARK: - Control Ack Sender

    /// Encode and send a `control_ack` JSON text frame to the client.
    func sendControlAck(_ ack: WSControlAckMessage) {
        if let data = try? JSONEncoder().encode(ack),
           let str = String(data: data, encoding: .utf8) {
            sendTextMessage(str)
        }
    }
}
