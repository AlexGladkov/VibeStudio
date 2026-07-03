// MARK: - GitCommitViewModel
// Observable view model encapsulating the commit sub-domain of the git sidebar:
// per-project commit input, commit execution, and AI commit-message generation.
// macOS 14+, Swift 5.10

import Foundation
import OSLog

// MARK: - GitCommitViewModel

/// Owns the commit-panel state and operations previously embedded in
/// `GitSidebarViewModel`.
///
/// Extracted to keep `GitSidebarViewModel` focused on project/status/branch
/// concerns. It is held by `GitSidebarViewModel` as a nested `@Observable`, so
/// SwiftUI observation is transparent for views reading
/// `gitSidebarVM.commit.commitSummaries[...]` etc.
///
/// After a successful commit the VM invokes ``onCommitted`` so the owner can
/// refresh git status/branches — the reload logic itself stays in
/// `GitSidebarViewModel`.
@Observable
@MainActor
final class GitCommitViewModel {

    // MARK: - Dependencies

    private let gitService: any GitServicing
    private let aiCommitService: any AICommitServicing

    /// Invoked after a successful commit so the owner can reload git info for
    /// the affected project. Set by `GitSidebarViewModel`.
    var onCommitted: ((Project) async -> Void)?

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

    // MARK: - Init

    init(
        gitService: any GitServicing,
        aiCommitService: any AICommitServicing
    ) {
        self.gitService = gitService
        self.aiCommitService = aiCommitService
    }

    // MARK: - Cleanup

    /// Release all cached commit state for a removed project.
    func cleanup(_ projectId: UUID) {
        commitSummaries.removeValue(forKey: projectId)
        commitDescriptions.removeValue(forKey: projectId)
        generatingAIProjects.remove(projectId)
        committingProjects.remove(projectId)
        commitPanelErrors.removeValue(forKey: projectId)
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
            await onCommitted?(project)
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
