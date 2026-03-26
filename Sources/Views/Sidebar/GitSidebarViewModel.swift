// MARK: - GitSidebarViewModel
// Observable view model encapsulating all git-related state and operations
// for the sidebar git panel.
// macOS 14+, Swift 5.10

import AppKit
import Foundation
import OSLog

// MARK: - GitSidebarViewModel

/// Manages git state for the sidebar: statuses, branches, commit input,
/// push/pull operations, and AI commit message generation.
///
/// Extracted from `SidebarView` to reduce its complexity and separate
/// business logic from the view layer.
@Observable
@MainActor
final class GitSidebarViewModel {

    // MARK: - Dependencies

    private let gitService: any GitServicing
    private let aiCommitService: any AICommitServicing

    // MARK: - Git Multi-Project State

    /// Expanded project rows in the git section.
    var gitExpandedProjects: Set<UUID> = []

    /// Cached git status per project.
    var projectGitStatuses: [UUID: GitStatus] = [:]

    /// Cached branch list per project.
    var projectBranches: [UUID: [GitBranch]] = [:]

    /// Projects that are not git repositories.
    var nonGitProjects: Set<UUID> = []

    /// Projects where remote branches could not be fetched.
    var remoteUnavailableProjects: Set<UUID> = []

    /// Branch fetch error messages per project.
    var projectBranchErrors: [UUID: String] = [:]

    /// Cached remote origin URL per project. nil value = no remote configured.
    var projectRemoteURLs: [UUID: String] = [:]

    // MARK: - Branch Operations In-Progress

    /// Keys of the form "\(projectId):\(branchName)" for branches being operated on.
    var branchOperationsInProgress: Set<String> = []

    // MARK: - Commit Panel State (per-project)

    var commitSummaries: [UUID: String] = [:]
    var commitDescriptions: [UUID: String] = [:]
    var generatingAIProjects: Set<UUID> = []
    var committingProjects: Set<UUID> = []
    var commitPanelErrors: [UUID: String] = [:]

    // MARK: - AI Diff Warning Dialog

    var showAIDiffWarning = false
    var pendingAIDiffProject: Project?
    var pendingAIDiffText: String?

    // MARK: - Checkout Error Alert

    /// Unified git operation error (checkout, pull, push).
    var checkoutErrorMessage: String?

    // MARK: - Init

    init(gitService: any GitServicing, aiCommitService: any AICommitServicing) {
        self.gitService = gitService
        self.aiCommitService = aiCommitService
    }

    // MARK: - Git Info Loading

    /// Load git status + branches for a single project.
    /// Each step is independent -- branches failure never blocks the UI.
    func loadGitInfo(for project: Project) async {
        // Step 1: status
        let loadedStatus: GitStatus?
        do {
            let status = try await gitService.status(at: project.path)
            projectGitStatuses[project.id] = status
            nonGitProjects.remove(project.id)
            loadedStatus = status
        } catch let error as GitServiceError {
            if case .notARepository = error {
                nonGitProjects.insert(project.id)
                projectGitStatuses.removeValue(forKey: project.id)
            } else {
                projectBranchErrors[project.id] = error.localizedDescription
            }
            if projectBranches[project.id] == nil {
                projectBranches[project.id] = []
            }
            return
        } catch {
            projectBranchErrors[project.id] = error.localizedDescription
            if projectBranches[project.id] == nil {
                projectBranches[project.id] = []
            }
            return
        }

        // Step 2: branches (independent -- failure shows fallback)
        do {
            let branches = try await gitService.branches(at: project.path)
            projectBranches[project.id] = branches
            remoteUnavailableProjects.remove(project.id)
            projectBranchErrors.removeValue(forKey: project.id)
        } catch {
            remoteUnavailableProjects.insert(project.id)
            if let s = loadedStatus, !s.branch.isEmpty {
                projectBranches[project.id] = [
                    GitBranch(name: s.branch, isRemote: false, isCurrent: true)
                ]
            } else {
                projectBranches[project.id] = []
            }
        }

        // Step 3: remote URL (independent -- nil if not configured)
        await loadRemoteURL(for: project)
    }

    /// Load and cache the remote origin URL for a project.
    func loadRemoteURL(for project: Project) async {
        if let url = await gitService.remoteURL(at: project.path) {
            projectRemoteURLs[project.id] = url
        } else {
            projectRemoteURLs.removeValue(forKey: project.id)
        }
    }

    /// Open the project's remote origin in the default browser.
    func openInRemote(project: Project) {
        guard let rawURL = projectRemoteURLs[project.id],
              let browserURL = GitURLConverter.browserURL(from: rawURL) else {
            return
        }
        NSWorkspace.shared.open(browserURL)
    }

    /// Refresh git status for all projects concurrently (header row badges only).
    func refreshAllGitInfo(projects: [Project]) async {
        let results = await withTaskGroup(
            of: (UUID, Result<GitStatus, Error>).self,
            returning: [(UUID, Result<GitStatus, Error>)].self
        ) { group in
            for project in projects {
                group.addTask { [gitService] in
                    do {
                        let status = try await gitService.status(at: project.path)
                        return (project.id, .success(status))
                    } catch {
                        return (project.id, .failure(error))
                    }
                }
            }

            var collected: [(UUID, Result<GitStatus, Error>)] = []
            for await result in group {
                collected.append(result)
            }
            return collected
        }

        for (projectId, result) in results {
            switch result {
            case .success(let status):
                projectGitStatuses[projectId] = status
                nonGitProjects.remove(projectId)
                projectBranchErrors.removeValue(forKey: projectId)
            case .failure(let error):
                if let gitError = error as? GitServiceError, case .notARepository = gitError {
                    nonGitProjects.insert(projectId)
                    projectGitStatuses.removeValue(forKey: projectId)
                }
            }
        }
    }

    // MARK: - Branch Context Menu Actions

    func gitBranchPull(_ branch: String, isCurrent: Bool, project: Project) async {
        let opKey = "\(project.id):\(branch)"
        branchOperationsInProgress.insert(opKey)
        defer { branchOperationsInProgress.remove(opKey) }

        do {
            let remote = await gitService.defaultRemote(for: branch, at: project.path)
            try await gitService.pullBranch(branch, isCurrent: isCurrent, remote: remote, at: project.path)
            await loadGitInfo(for: project)
        } catch let gitError as GitServiceError {
            switch gitError {
            case .commandFailed(_, _, let stderr):
                checkoutErrorMessage = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            case .mergeConflict(let files):
                checkoutErrorMessage = "Merge conflict in: \(files.joined(separator: ", "))"
            default:
                checkoutErrorMessage = gitError.localizedDescription
            }
        } catch {
            checkoutErrorMessage = error.localizedDescription
        }
    }

    func gitBranchPush(_ branch: String, project: Project) async {
        let opKey = "\(project.id):\(branch)"
        branchOperationsInProgress.insert(opKey)
        defer { branchOperationsInProgress.remove(opKey) }

        do {
            let remote = await gitService.defaultRemote(for: branch, at: project.path)
            try await gitService.pushBranch(branch, remote: remote, at: project.path)
            await loadGitInfo(for: project)
        } catch let gitError as GitServiceError {
            switch gitError {
            case .commandFailed(_, _, let stderr):
                checkoutErrorMessage = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            case .pushRejected(let reason):
                checkoutErrorMessage = reason.trimmingCharacters(in: .whitespacesAndNewlines)
            default:
                checkoutErrorMessage = gitError.localizedDescription
            }
        } catch {
            checkoutErrorMessage = error.localizedDescription
        }
    }

    // MARK: - Checkout

    func checkout(branch: String, project: Project) async {
        do {
            try await gitService.checkout(branch: branch, at: project.path)
            await loadGitInfo(for: project)
        } catch let gitError as GitServiceError {
            if case .commandFailed(_, _, let stderr) = gitError {
                checkoutErrorMessage = stderr
                    .trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                checkoutErrorMessage = gitError.localizedDescription
            }
        } catch {
            checkoutErrorMessage = error.localizedDescription
        }
    }

    // MARK: - Init Repository

    func initRepository(for project: Project) async {
        do {
            try await gitService.initRepository(at: project.path)
            await loadGitInfo(for: project)
        } catch {
            Logger.git.error("git init failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Commit Actions

    func performCommit(for project: Project) async {
        let summary = (commitSummaries[project.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        guard !summary.isEmpty else {
            commitPanelErrors[project.id] = "Commit summary cannot be empty"
            return
        }

        let description = (commitDescriptions[project.id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        let fullMessage = description.isEmpty ? summary : "\(summary)\n\n\(description)"

        committingProjects.insert(project.id)
        commitPanelErrors.removeValue(forKey: project.id)
        defer { committingProjects.remove(project.id) }

        do {
            try await gitService.stage(files: [], at: project.path)
            try await gitService.commit(message: fullMessage, at: project.path)
            commitSummaries.removeValue(forKey: project.id)
            commitDescriptions.removeValue(forKey: project.id)
            await loadGitInfo(for: project)
        } catch let gitError as GitServiceError {
            if case .commandFailed(_, _, let stderr) = gitError {
                commitPanelErrors[project.id] = stderr.trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                commitPanelErrors[project.id] = gitError.localizedDescription
            }
        } catch {
            commitPanelErrors[project.id] = error.localizedDescription
        }
    }

    /// First step of AI commit: get diff and show confirmation dialog.
    func generateAICommitMessage(for project: Project) async {
        commitPanelErrors.removeValue(forKey: project.id)

        do {
            let diff = try await gitService.headDiff(at: project.path)
            guard !diff.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                commitPanelErrors[project.id] = "No changes to analyze"
                return
            }

            pendingAIDiffText = diff
            pendingAIDiffProject = project
            showAIDiffWarning = true
        } catch {
            commitPanelErrors[project.id] = "AI: \(error.localizedDescription)"
        }
    }

    /// Called after the user confirms the AI diff warning dialog.
    func sendAIDiff(_ diff: String, for project: Project) async {
        generatingAIProjects.insert(project.id)
        commitPanelErrors.removeValue(forKey: project.id)
        defer { generatingAIProjects.remove(project.id) }

        do {
            let truncated = String(diff.prefix(AIConstants.maxDiffLength))
            let result = try await aiCommitService.generateCommitMessage(for: truncated)

            let trimmed = result.trimmingCharacters(in: .whitespacesAndNewlines)
            let lines = trimmed.components(separatedBy: "\n")
            let summaryLine = lines.first?.trimmingCharacters(in: .whitespaces) ?? ""
            let descPart = lines.dropFirst()
                .drop(while: { $0.trimmingCharacters(in: .whitespaces).isEmpty })
            let descText = Array(descPart).joined(separator: "\n")
                .trimmingCharacters(in: .whitespacesAndNewlines)

            commitSummaries[project.id] = summaryLine
            commitDescriptions[project.id] = descText
        } catch {
            commitPanelErrors[project.id] = "AI: \(error.localizedDescription)"
        }
    }
}
