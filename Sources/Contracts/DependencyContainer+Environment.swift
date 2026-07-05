// MARK: - Dependency Injection — Environment
// SwiftUI EnvironmentKey / EnvironmentValues интеграция для ServiceContainer
// плюс preview/no-op реализации, используемые как default значения.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SwiftUI Environment Keys

/// EnvironmentKey для каждого сервиса.
/// Позволяет внедрять сервисы через @Environment в любой View.

private struct ProjectManagerKey: EnvironmentKey {
    @MainActor static let defaultValue: any ProjectManaging = PreviewProjectManager()
}

private struct TerminalSessionManagerKey: EnvironmentKey {
    @MainActor static let defaultValue: any TerminalSessionManaging = PreviewTerminalSessionManager()
}

private struct GitServiceKey: EnvironmentKey {
    static let defaultValue: any GitServicing = PreviewGitService()
}

private struct FileSystemWatcherKey: EnvironmentKey {
    static let defaultValue: any FileSystemWatching = PreviewFileSystemWatcher()
}

private struct SessionPersistenceKey: EnvironmentKey {
    static let defaultValue: any SessionPersisting = PreviewSessionPersistence()
}

private struct AICommitServiceKey: EnvironmentKey {
    static let defaultValue: any AICommitServicing = PreviewAICommitService()
}

private struct GitStatusPollerKey: EnvironmentKey {
    @MainActor static let defaultValue: any GitStatusPolling = PreviewGitStatusPoller()
}

private struct AgentAvailabilityKey: EnvironmentKey {
    @MainActor static let defaultValue: any AgentAvailabilityChecking = PreviewAgentAvailability()
}

private struct UpdateServiceKey: EnvironmentKey {
    @MainActor static let defaultValue: any UpdateServicing = PreviewUpdateService()
}

private struct NavigationCoordinatorKey: EnvironmentKey {
    @MainActor static let defaultValue: AppNavigationCoordinator = AppNavigationCoordinator()
}

private struct ThemeServiceKey: EnvironmentKey {
    @MainActor static let defaultValue: ThemeService = ThemeService()
}

private struct FreeTabStoreKey: EnvironmentKey {
    @MainActor static let defaultValue: FreeTabStore = FreeTabStore()
}

private struct CodeSpeakServiceKey: EnvironmentKey {
    @MainActor static let defaultValue: CodeSpeakService = CodeSpeakService()
}

private struct SyntaxParserRegistryKey: EnvironmentKey {
    @MainActor static let defaultValue: SyntaxParserRegistry = SyntaxParserRegistry()
}

private struct CodeSpeakPreferencesKey: EnvironmentKey {
    @MainActor static let defaultValue: CodeSpeakPreferences = CodeSpeakPreferences()
}

private struct GeneralPreferencesKey: EnvironmentKey {
    @MainActor static let defaultValue: GeneralPreferences = GeneralPreferences()
}

private struct RemoteControlServerKey: EnvironmentKey {
    @MainActor static let defaultValue: RemoteControlServer = RemoteControlServer()
}

private struct RemoteControlPreferencesKey: EnvironmentKey {
    @MainActor static let defaultValue: RemoteControlPreferences = RemoteControlPreferences()
}

extension EnvironmentValues {
    var projectManager: any ProjectManaging {
        get { self[ProjectManagerKey.self] }
        set { self[ProjectManagerKey.self] = newValue }
    }

    var terminalSessionManager: any TerminalSessionManaging {
        get { self[TerminalSessionManagerKey.self] }
        set { self[TerminalSessionManagerKey.self] = newValue }
    }

    var gitService: any GitServicing {
        get { self[GitServiceKey.self] }
        set { self[GitServiceKey.self] = newValue }
    }

    var fileSystemWatcher: any FileSystemWatching {
        get { self[FileSystemWatcherKey.self] }
        set { self[FileSystemWatcherKey.self] = newValue }
    }

    var sessionPersistence: any SessionPersisting {
        get { self[SessionPersistenceKey.self] }
        set { self[SessionPersistenceKey.self] = newValue }
    }

    var aiCommitService: any AICommitServicing {
        get { self[AICommitServiceKey.self] }
        set { self[AICommitServiceKey.self] = newValue }
    }

    var gitStatusPoller: any GitStatusPolling {
        get { self[GitStatusPollerKey.self] }
        set { self[GitStatusPollerKey.self] = newValue }
    }

    var agentAvailability: any AgentAvailabilityChecking {
        get { self[AgentAvailabilityKey.self] }
        set { self[AgentAvailabilityKey.self] = newValue }
    }

    var updateService: any UpdateServicing {
        get { self[UpdateServiceKey.self] }
        set { self[UpdateServiceKey.self] = newValue }
    }

    var navigationCoordinator: AppNavigationCoordinator {
        get { self[NavigationCoordinatorKey.self] }
        set { self[NavigationCoordinatorKey.self] = newValue }
    }

    var themeService: ThemeService {
        get { self[ThemeServiceKey.self] }
        set { self[ThemeServiceKey.self] = newValue }
    }

    var freeTabStore: FreeTabStore {
        get { self[FreeTabStoreKey.self] }
        set { self[FreeTabStoreKey.self] = newValue }
    }

    var codeSpeak: CodeSpeakService {
        get { self[CodeSpeakServiceKey.self] }
        set { self[CodeSpeakServiceKey.self] = newValue }
    }

    var syntaxParserRegistry: SyntaxParserRegistry {
        get { self[SyntaxParserRegistryKey.self] }
        set { self[SyntaxParserRegistryKey.self] = newValue }
    }

    var csPreferences: CodeSpeakPreferences {
        get { self[CodeSpeakPreferencesKey.self] }
        set { self[CodeSpeakPreferencesKey.self] = newValue }
    }

    var generalPreferences: GeneralPreferences {
        get { self[GeneralPreferencesKey.self] }
        set { self[GeneralPreferencesKey.self] = newValue }
    }

    var remoteControlServer: RemoteControlServer {
        get { self[RemoteControlServerKey.self] }
        set { self[RemoteControlServerKey.self] = newValue }
    }

    var remoteControlPreferences: RemoteControlPreferences {
        get { self[RemoteControlPreferencesKey.self] }
        set { self[RemoteControlPreferencesKey.self] = newValue }
    }
}

// MARK: - Preview / Test implementations (safe no-ops)

// Эти реализации используются как default value в EnvironmentKey.
// Возвращают пустые/нейтральные значения — без crash.
// Позволяют использовать SwiftUI Previews без инъекции реальных сервисов.

@Observable
@MainActor
private final class PreviewProjectManager: ProjectManaging {
    var projects: [Project] = []
    var activeProjectId: UUID?
    var recentHistory: [Project] = []
    var recentProjects: [Project] = []
    func addProject(at path: URL) throws -> Project {
        throw ProjectManagerError.invalidPath(path)
    }
    func removeProject(_ id: UUID) throws {}
    func updateProject(_ id: UUID, _ mutate: (inout Project) -> Void) throws {}
    func moveProjects(from indices: IndexSet, to destination: Int) {}
    func project(for id: UUID) -> Project? { nil }
    func project(at path: URL) -> Project? { nil }
    func load() throws {}
    func save() throws {}
}

@Observable
@MainActor
private final class PreviewTerminalSessionManager: TerminalSessionManaging {
    var sessionsByProject: [UUID: [TerminalSession]] = [:]
    var projectActivityStates: [UUID: TabActivityState] = [:]
    func createSession(
        for projectId: UUID,
        shell: String?,
        workingDirectory: URL?,
        size: TerminalSize
    ) throws -> TerminalSession {
        throw TerminalSessionError.sessionLimitReached(projectId: projectId, max: 0)
    }
    func attachView(to sessionId: UUID) throws -> NSView {
        throw TerminalSessionError.sessionNotFound(sessionId)
    }
    func detachView(from sessionId: UUID) {}
    func resize(session sessionId: UUID, to size: TerminalSize) {}
    func killSession(_ sessionId: UUID, force: Bool) {}
    func killAllSessions(for projectId: UUID) {}
    func split(
        _ sessionId: UUID,
        direction: SplitDirection,
        size: TerminalSize
    ) throws -> TerminalSession {
        throw TerminalSessionError.sessionNotFound(sessionId)
    }
    func session(for id: UUID) -> TerminalSession? { nil }
    func sessions(for projectId: UUID) -> [TerminalSession] { [] }
    var sessionEvents: AsyncStream<TerminalSessionEvent> {
        AsyncStream(TerminalSessionEvent.self) { $0.finish() }
    }
    func scrollbackContent(for sessionId: UUID) -> String? { nil }
    func sendInput(_ text: String, to sessionId: UUID) {}
    func markProjectSeen(_ projectId: UUID) {}
    @discardableResult
    func startAgentSession(
        agent: AIAssistant,
        for projectId: UUID,
        workingDirectory: String,
        apiKeyValue: String?
    ) -> TerminalSession? { nil }
}

private final class PreviewGitService: GitServicing {
    func status(at repository: URL) async throws -> GitStatus { .empty }
    func diff(file: String, staged: Bool, at repository: URL) async throws -> [GitDiffHunk] { [] }
    func fullStagedDiff(at repository: URL) async throws -> String { "" }
    func branches(at repository: URL) async throws -> [GitBranch] { [] }
    func log(limit: Int, at repository: URL) async throws -> [GitCommitInfo] { [] }
    func stage(files: [String], at repository: URL) async throws {}
    func unstage(files: [String], at repository: URL) async throws {}
    func commit(message: String, at repository: URL) async throws -> String { "" }
    func push(remote: String, at repository: URL) async throws {}
    func pull(remote: String, at repository: URL) async throws {}
    func fetch(remote: String, at repository: URL) async throws {}
    func pushBranch(_ branch: String, remote: String, at repository: URL) async throws {}
    func pullBranch(_ branch: String, isCurrent: Bool, remote: String, at repository: URL) async throws {}
    func headDiff(at repository: URL) async throws -> String { "" }
    func defaultRemote(for branch: String?, at repository: URL) async -> String { "origin" }
    func checkout(branch: String, at repository: URL) async throws {}
    func createBranch(name: String, from startPoint: String?, at repository: URL) async throws {}
    func isRepository(at path: URL) async -> Bool { false }
    func repositoryRoot(for path: URL) async throws -> URL {
        throw GitServiceError.notARepository(path: path)
    }
    func initRepository(at path: URL) async throws {}
    func addRemote(name: String, url: String, at repository: URL) async throws {}
    func remoteURL(name: String, at repository: URL) async -> String? { nil }
    func aheadBehind(at repository: URL) async throws -> (ahead: Int, behind: Int) { (0, 0) }
    func diffStats(at repository: URL) async throws -> [String: GitDiffStat] { [:] }
}

private final class PreviewFileSystemWatcher: FileSystemWatching {
    @discardableResult
    func watch(directory: URL, options: WatchOptions) throws -> WatchToken { WatchToken() }
    func unwatch(_ token: WatchToken) {}
    func unwatchAll() {}
    var events: AsyncStream<FileChangeEvent> {
        AsyncStream(FileChangeEvent.self) { $0.finish() }
    }
    var activeWatches: [WatchInfo] { [] }
}

private final class PreviewSessionPersistence: SessionPersisting {
    func save(snapshot: AppSessionSnapshot) async throws {}
    func restore() async throws -> AppSessionSnapshot? { nil }
    func clear() async throws {}
    func saveScrollback(_ content: String, for sessionId: UUID) async throws {}
    func loadScrollback(for sessionId: UUID) async -> String? { nil }
    func deleteScrollback(for sessionId: UUID) async throws {}
    func pruneOrphanedScrollbacks(keeping activeSessionIds: Set<UUID>) async throws -> Int { 0 }
    var storageDirectory: URL { URL(fileURLWithPath: NSTemporaryDirectory()) }
    var currentSnapshotVersion: Int { 0 }
}

@MainActor
private final class PreviewUpdateService: UpdateServicing {
    var canCheckForUpdates: Bool { false }
    var automaticallyChecksForUpdates: Bool = false
    var currentVersion: String { "0.0.0" }
    func checkForUpdates() {}
}

private final class PreviewAICommitService: AICommitServicing {
    func generateCommitMessage(for diff: String) async throws -> String {
        throw AICommitServiceError.missingAPIKey
    }
}

@Observable
@MainActor
private final class PreviewGitStatusPoller: GitStatusPolling {
    var status: GitStatus = .empty
    var isPolling: Bool = false
    var lastError: Error?
    func startPolling(for repository: URL, isActive: Bool) {}
    func stopPolling() {}
    func refreshNow() {}
}

@Observable
@MainActor
private final class PreviewAgentAvailability: AgentAvailabilityChecking {
    var availability: [AIAssistant: AgentAvailabilityStatus] = {
        var dict: [AIAssistant: AgentAvailabilityStatus] = [:]
        for agent in AIAssistant.allCases {
            dict[agent] = .notInstalled(installHint: agent.installHint)
        }
        return dict
    }()
    func refreshAll() {}
    func check(_ agent: AIAssistant) -> AgentAvailabilityStatus {
        availability[agent] ?? .notInstalled(installHint: agent.installHint)
    }
    func canLaunch(_ agent: AIAssistant) -> Bool { false }
}
