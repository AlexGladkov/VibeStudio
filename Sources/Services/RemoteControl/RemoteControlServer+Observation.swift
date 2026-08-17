// MARK: - RemoteControlServer+Observation
// Iteration 9 split + Cost Tracker broadcast (Feature #2).
// Iteration 9 split. The two long-lived observation loops (ngrok host tracking
// + session-change broadcasting) extracted from ``RemoteControlServer`` to keep
// the primary type body under SwiftLint's `type_body_length` budget.
//
// Both loops bridge `@Observable` mutations to an AsyncSequence via the shared
// ``AsyncObservation/stream(of:keyPath:emitInitial:)`` helper instead of hand-
// rolling `withObservationTracking` + `ContinuationHolder`.
//
// macOS 14+, Swift 5.10

import Foundation
import OSLog

extension RemoteControlServer {

    // MARK: - Cost Update Observation

    /// Watch `CostTrackerService.sessionCosts` for changes and broadcast
    /// `cost_update` WebSocket messages to the session's bridge (BOLA:
    /// only the bridge whose `sessionId` matches receives the message).
    ///
    /// Throttle: max 1 broadcast per 500ms to avoid flooding mobile clients.
    func startCostObservation() {
        guard let tracker = costTrackerService else { return }
        _costObservationTask = Task { @MainActor [weak self, weak tracker] in
            guard let self, let tracker else { return }
            let encoder = JSONEncoder()
            // Debounce: track last-sent snapshot per session to skip no-change wakeups.
            var lastSent: [UUID: SessionCostSnapshot] = [:]

            let stream = AsyncObservation.stream(emitInitial: false) { [weak tracker] () -> Int? in
                guard let tracker else { return nil }
                return tracker.sessionCosts.values.reduce(0) { $0 + $1.totalTokens }
            }
            for await _ in stream {
                guard !Task.isCancelled else { return }
                // 500ms throttle
                try? await Task.sleep(for: .milliseconds(500))
                guard !Task.isCancelled else { return }

                for (sessionId, snap) in tracker.sessionCosts {
                    guard snap.totalTokens > 0 else { continue }
                    // Skip if nothing changed since last broadcast for this session.
                    if lastSent[sessionId] == snap { continue }
                    lastSent[sessionId] = snap

                    // Find project for this session (required by WSCostUpdateMessage).
                    // Reverse-lookup via sessionsByProject (no direct projectId API on TerminalService).
                    guard let projectId = self.terminalService.sessionsByProject.first(where: {
                        $0.value.contains(where: { $0.id == sessionId })
                    })?.key else { continue }

                    let msg = WSCostUpdateMessage(
                        type: "cost_update",
                        sessionId: sessionId.uuidString,
                        projectId: projectId.uuidString,
                        promptTokens: snap.promptTokens,
                        completionTokens: snap.completionTokens,
                        totalTokens: snap.totalTokens,
                        estimatedCostUsd: snap.costUSD,
                        model: snap.model
                    )
                    if let data = try? encoder.encode(msg),
                       let json = String(data: data, encoding: .utf8) {
                        // BOLA: only send to the bridge attached to this session.
                        self.broadcastToSession(sessionId, json: json)
                        Logger.remoteControl.debug(
                            "RemoteControlServer: cost_update broadcast session=\(sessionId, privacy: .public) tokens=\(snap.totalTokens, privacy: .public)"
                        )
                    }
                }
            }
        }
    }


    // MARK: - Ngrok Host Observation

    /// Observe `NgrokTunnelService.tunnelURL` and push the resolved host into
    /// `ngrokHostRef` so all active ``HTTPRequestRouter`` instances receive it.
    func startNgrokHostObservation() {
        ngrokHostObservationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let ngrokSvc = self.ngrok
            // `AsyncObservation.stream` re-arms `withObservationTracking` after
            // each change, bridging it to an AsyncSequence. `emitInitial: false`
            // preserves the original semantics of only reacting to *changes*.
            let stream = AsyncObservation.stream(
                of: ngrokSvc,
                keyPath: \.tunnelURL,
                emitInitial: false
            )
            for await rawURL in stream {
                guard !Task.isCancelled else { return }

                let host: String?
                if let rawURL,
                   let parsed = URL(string: rawURL),
                   let h = parsed.host {
                    host = h
                } else {
                    host = nil
                }
                self.ngrokHostRef.set(host)
                Logger.remoteControl.debug(
                    "RemoteControlServer: ngrok CORS host updated to \(host ?? "nil", privacy: .public)"
                )
            }
        }
    }

    // MARK: - Session Observation

    /// Observe `TerminalService.sessionsByProject` and broadcast
    /// `sessions_changed` messages to all connected WebSocket clients.
    func startSessionObservation() {
        sessionObservationTask = Task { @MainActor [weak self] in
            guard let self else { return }
            let terminalService = self.terminalService
            // Bridge `sessionsByProject` mutations to an AsyncSequence via the
            // shared observation helper. `emitInitial: false` keeps the original
            // change-only semantics.
            let stream = AsyncObservation.stream(
                of: terminalService,
                keyPath: \.sessionsByProject,
                emitInitial: false
            )
            // Reused across every wakeup — see `sessionsEncoder` declaration.
            let encoder = self.sessionsEncoder
            // NOTE: broadcast still fans out to ALL projects/bridges on any
            // change (O(sessions×bridges)). Scoping the broadcast to only the
            // mutated project would require diffing `sessionsByProject`, which
            // risks the observation semantics; left as-is. After W2#1 the wakeup
            // frequency drops sharply (no per-tick re-writes), so this loop now
            // fires only on real session add/remove/state transitions.
            for await _ in stream {
                guard !Task.isCancelled else { return }

                // Sessions changed — broadcast to all bridges.
                for (projectId, sessions) in terminalService.sessionsByProject {
                    let sessionResponses = sessions.map { session in
                        SessionResponse(
                            id: session.id.uuidString,
                            title: session.title,
                            state: session.state.remoteAPIString,
                            isAgent: session.isAgentSession,
                            hasRemoteAttachment: self.activeBridges.values.contains {
                                $0.sessionId == session.id
                            },
                            attachedDeviceId: self.activeBridges.values.first {
                                $0.sessionId == session.id
                            }?.deviceId.uuidString
                        )
                    }
                    let msg = WSSessionsChangedMessage(
                        type: "sessions_changed",
                        projectId: projectId.uuidString,
                        sessions: sessionResponses
                    )
                    if let data = try? encoder.encode(msg),
                       let json = String(data: data, encoding: .utf8) {
                        self.broadcastTextMessage(json)
                    }
                }
            }
        }
    }
}
