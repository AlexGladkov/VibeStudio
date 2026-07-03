// MARK: - RemoteControlServer+Observation
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
            for await _ in stream {
                guard !Task.isCancelled else { return }

                // Sessions changed — broadcast to all bridges.
                let encoder = JSONEncoder()
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
