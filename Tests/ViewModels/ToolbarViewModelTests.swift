import XCTest
@testable import VibeStudio

@MainActor
final class ToolbarViewModelTests: XCTestCase {

    private var projectManager: MockProjectManager!
    private var terminalManager: MockTerminalSessionManager!
    private var agentAvailability: MockAgentAvailability!
    private var apiKeyResolver: MockAPIKeyResolver!

    override func setUp() {
        super.setUp()
        projectManager = MockProjectManager()
        terminalManager = MockTerminalSessionManager()
        agentAvailability = MockAgentAvailability()
        apiKeyResolver = MockAPIKeyResolver()
    }

    override func tearDown() {
        projectManager = nil
        terminalManager = nil
        agentAvailability = nil
        apiKeyResolver = nil
        super.tearDown()
    }

    private func makeSUT() -> ToolbarViewModel {
        ToolbarViewModel(
            projectManager: projectManager,
            terminalManager: terminalManager,
            agentAvailability: agentAvailability,
            apiKeyResolver: apiKeyResolver
        )
    }

    // MARK: - Init / Deinit

    func testInit_createsWithoutCrash() {
        let sut = makeSUT()
        XCTAssertFalse(sut.isRunning)
        XCTAssertEqual(sut.currentAssistant, .claude)
    }

    func testDeinit_cancelsTasksCleanly() async throws {
        var sut: ToolbarViewModel? = makeSUT()
        weak var weakSut = sut

        // Give observation tasks a chance to start.
        try await Task.sleep(for: .milliseconds(50))

        // Finish the event stream so sessionEventTask can complete.
        terminalManager.finishEvents()

        sut = nil

        // Allow async cleanup.
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertNil(weakSut, "ToolbarViewModel should be deallocated after nilling — no retain cycle")
    }

    // MARK: - No Active Project

    func testIsRunning_noActiveProject_returnsFalse() {
        let sut = makeSUT()
        XCTAssertNil(sut.activeId)
        XCTAssertFalse(sut.isRunning)
    }

    func testCurrentAssistant_noActiveProject_returnsClaude() {
        let sut = makeSUT()
        XCTAssertEqual(sut.currentAssistant, .claude)
    }

    func testStartAssistant_noActiveProject_doesNothing() {
        let sut = makeSUT()
        sut.startAssistant()
        XCTAssertTrue(terminalManager.sendInputCalls.isEmpty)
        XCTAssertTrue(terminalManager.startAgentSessionCalls.isEmpty)
    }

    // MARK: - Select Assistant

    func testSelectAssistant_storesPerProject() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        let sut = makeSUT()
        sut.selectAssistant(.opencode)

        XCTAssertEqual(sut.currentAssistant, .opencode)
    }

    func testSelectAssistant_noActiveProject_noop() {
        let sut = makeSUT()
        sut.selectAssistant(.gemini)
        XCTAssertEqual(sut.currentAssistant, .claude, "Without active project, selection should not persist")
    }

    // MARK: - Start / Stop (launchViaShellInput)

    func testStartAssistant_viaShellInput_sendsCommand() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        // Create a shell session (non-agent) for the project.
        let shellSession = try terminalManager.createSession(for: project.id)

        let sut = makeSUT()
        // opencode uses launchViaShellInput=true.
        sut.selectAssistant(.opencode)
        sut.startAssistant()

        XCTAssertTrue(sut.isRunning)
        XCTAssertEqual(terminalManager.sendInputCalls.count, 1)
        XCTAssertEqual(terminalManager.sendInputCalls.first?.sessionId, shellSession.id)
    }

    func testStopAssistant_sendsCtrlC() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id
        _ = try terminalManager.createSession(for: project.id)

        let sut = makeSUT()
        // opencode uses launchViaShellInput → Ctrl-C exit sequence.
        sut.selectAssistant(.opencode)
        sut.startAssistant()
        XCTAssertTrue(sut.isRunning)

        sut.stopAssistant()
        let ctrlCCalls = terminalManager.sendInputCalls.filter { $0.text == "\u{03}" }
        XCTAssertFalse(ctrlCCalls.isEmpty, "stopAssistant should send Ctrl-C")
    }

    // MARK: - Start (dedicated PTY)

    func testStartAssistant_dedicatedPTY_createsAgentSession() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        // claude uses dedicated PTY (launchViaShellInput=false).
        let agentSessionId = UUID()
        terminalManager.startAgentSessionResult = TerminalSession(
            id: agentSessionId,
            projectId: project.id,
            title: "claude",
            isAgentSession: true
        )

        let sut = makeSUT()
        sut.selectAssistant(.claude)
        sut.startAssistant()

        XCTAssertTrue(sut.isRunning)
        XCTAssertEqual(terminalManager.startAgentSessionCalls.count, 1)
        XCTAssertEqual(terminalManager.startAgentSessionCalls.first?.agent, .claude)
    }

    func testStartAssistant_agentNotAvailable_doesNotLaunch() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        agentAvailability.canLaunchResult = false

        let sut = makeSUT()
        sut.startAssistant()

        XCTAssertFalse(sut.isRunning)
        XCTAssertTrue(terminalManager.sendInputCalls.isEmpty)
        XCTAssertTrue(terminalManager.startAgentSessionCalls.isEmpty)
    }

    // MARK: - Session Event: Process Exited

    func testSessionEvent_processExited_clearsRunningState() async throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        // Use dedicated PTY path so agentSessionIds gets populated.
        let agentSessionId = UUID()
        terminalManager.startAgentSessionResult = TerminalSession(
            id: agentSessionId,
            projectId: project.id,
            title: "claude",
            isAgentSession: true
        )

        let sut = makeSUT()
        sut.selectAssistant(.claude)
        sut.startAssistant()
        XCTAssertTrue(sut.isRunning)

        // Yield process exit event for the agent's session.
        terminalManager.yieldEvent(
            .processExited(sessionId: agentSessionId, projectId: project.id, exitCode: 0)
        )

        // Allow the async stream consumer to process.
        try await Task.sleep(for: .milliseconds(200))

        XCTAssertFalse(sut.isRunning, "Running state should clear after agent process exits")
    }

    // MARK: - Cleanup

    func testCleanupProject_removesPerProjectState() throws {
        let project = try projectManager.addProject(at: URL(fileURLWithPath: "/tmp/test"))
        projectManager.activeProjectId = project.id

        let sut = makeSUT()
        sut.selectAssistant(.opencode)

        sut.cleanupProject(project.id)

        // After cleanup, assistant should reset to default.
        XCTAssertEqual(sut.currentAssistant, .claude)
    }

    // MARK: - Production URL

    func testActiveProductionURL_noProject_returnsNil() {
        let sut = makeSUT()
        XCTAssertNil(sut.activeProductionURL)
    }

    func testRefreshAgentAvailability_delegates() {
        let sut = makeSUT()
        sut.refreshAgentAvailability()
        XCTAssertEqual(agentAvailability.refreshAllCallCount, 1)
    }
}
