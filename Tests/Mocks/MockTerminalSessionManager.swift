import Foundation
import AppKit
@testable import VibeStudio

/// Mock implementation of ``TerminalSessionManaging`` for unit tests.
///
/// In-memory sessions with configurable `sessionEvents` AsyncStream.
/// Call tracking for verification.
@Observable
@MainActor
final class MockTerminalSessionManager: TerminalSessionManaging {

    // MARK: - Observable State

    var sessionsByProject: [UUID: [TerminalSession]] = [:]
    var projectActivityStates: [UUID: TabActivityState] = [:]

    // MARK: - Stream

    @ObservationIgnored
    private var eventContinuation: AsyncStream<TerminalSessionEvent>.Continuation?
    @ObservationIgnored
    private(set) var sessionEvents: AsyncStream<TerminalSessionEvent>

    init() {
        var cont: AsyncStream<TerminalSessionEvent>.Continuation?
        sessionEvents = AsyncStream { continuation in
            cont = continuation
        }
        eventContinuation = cont
    }

    /// Yield an event into the sessionEvents stream.
    func yieldEvent(_ event: TerminalSessionEvent) {
        eventContinuation?.yield(event)
    }

    /// Finish the event stream.
    func finishEvents() {
        eventContinuation?.finish()
    }

    // MARK: - Call Tracking

    var createSessionCallCount = 0
    var killSessionCalls: [(sessionId: UUID, force: Bool)] = []
    var killAllSessionsCalls: [UUID] = []
    var sendInputCalls: [(text: String, sessionId: UUID)] = []
    var markProjectSeenCalls: [UUID] = []
    var startAgentSessionCalls: [(agent: AIAssistant, projectId: UUID)] = []

    // MARK: - Configurable Returns

    var startAgentSessionResult: TerminalSession?

    // MARK: - TerminalSessionCreating

    @discardableResult
    func createSession(
        for projectId: UUID,
        shell: String?,
        workingDirectory: URL?,
        size: TerminalSize
    ) throws -> TerminalSession {
        createSessionCallCount += 1
        let session = TerminalSession(
            id: UUID(),
            projectId: projectId,
            title: "mock-shell"
        )
        sessionsByProject[projectId, default: []].append(session)
        return session
    }

    func attachView(to sessionId: UUID) throws -> NSView {
        NSView()
    }

    func detachView(from sessionId: UUID) {}

    func resize(session sessionId: UUID, to size: TerminalSize) {}

    func killSession(_ sessionId: UUID, force: Bool) {
        killSessionCalls.append((sessionId, force))
        for (projectId, sessions) in sessionsByProject {
            sessionsByProject[projectId] = sessions.filter { $0.id != sessionId }
        }
    }

    func killAllSessions(for projectId: UUID) {
        killAllSessionsCalls.append(projectId)
        sessionsByProject.removeValue(forKey: projectId)
    }

    @discardableResult
    func split(
        _ sessionId: UUID,
        direction: SplitDirection,
        size: TerminalSize
    ) throws -> TerminalSession {
        let session = TerminalSession(id: UUID(), projectId: UUID(), title: "mock-split")
        return session
    }

    @discardableResult
    func startAgentSession(
        agent: AIAssistant,
        for projectId: UUID,
        workingDirectory: String,
        apiKeyValue: String?
    ) -> TerminalSession? {
        startAgentSessionCalls.append((agent, projectId))
        if let result = startAgentSessionResult {
            let session = TerminalSession(
                id: result.id,
                projectId: projectId,
                title: agent.displayName,
                isAgentSession: true
            )
            sessionsByProject[projectId, default: []].append(session)
            return session
        }
        return nil
    }

    // MARK: - TerminalSessionQuerying

    func session(for id: UUID) -> TerminalSession? {
        sessionsByProject.values.flatMap { $0 }.first { $0.id == id }
    }

    func sessions(for projectId: UUID) -> [TerminalSession] {
        sessionsByProject[projectId] ?? []
    }

    func markProjectSeen(_ projectId: UUID) {
        markProjectSeenCalls.append(projectId)
    }

    // MARK: - TerminalInputSending

    func sendInput(_ text: String, to sessionId: UUID) {
        sendInputCalls.append((text, sessionId))
    }

    // MARK: - TerminalScrollbackAccessing

    // P2-4: scrollbackContent(for:) removed from protocol; protocol now
    // requires rawScrollbackContent(for:) only.
    func rawScrollbackContent(for sessionId: UUID) -> String? {
        nil
    }
}
