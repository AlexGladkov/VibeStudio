// MARK: - CommitPanelView
// Commit input panel pinned to the bottom of the git sidebar section.
// Extracted from SidebarView to reduce complexity.
// macOS 14+, Swift 5.10

import SwiftUI

/// Commit panel displaying summary/description text fields, AI generation button,
/// and a commit action button. Operates on a single active project.
struct CommitPanelView: View {
    let project: Project
    let gitSidebarVM: GitSidebarViewModel

    var body: some View {
        let gitStatus = gitSidebarVM.projectGitStatuses[project.id]
        let hasChanges = !(gitStatus?.stagedFiles.isEmpty ?? true)
            || !(gitStatus?.unstagedFiles.isEmpty ?? true)
            || !(gitStatus?.untrackedFiles.isEmpty ?? true)
        let isGenerating = gitSidebarVM.generatingAIProjects.contains(project.id)
        let isCommitting = gitSidebarVM.committingProjects.contains(project.id)
        let summaryText = gitSidebarVM.commitSummaries[project.id] ?? ""
        let charCount = summaryText.count
        let isOverLimit = charCount > 72

        VStack(alignment: .leading, spacing: DSSpacing.xs) {
            Rectangle()
                .fill(DSColor.borderSubtle)
                .frame(height: 1)
                .padding(.vertical, 2)

            // Input area
            VStack(alignment: .leading, spacing: 0) {
                // Summary + AI button
                HStack(alignment: .top, spacing: DSSpacing.xs) {
                    TextField(
                        "Commit summary",
                        text: Binding(
                            get: { gitSidebarVM.commitSummaries[project.id] ?? "" },
                            set: { gitSidebarVM.commitSummaries[project.id] = $0 }
                        ),
                        axis: .vertical
                    )
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .textFieldStyle(.plain)
                    .lineLimit(1...3)
                    .disabled(!hasChanges || isCommitting)
                    .opacity(hasChanges ? 1 : 0.35)

                    Button {
                        Task { await gitSidebarVM.generateAICommitMessage(for: project) }
                    } label: {
                        if isGenerating {
                            ProgressView().scaleEffect(0.5).frame(width: 20, height: 20)
                        } else {
                            Image(systemName: "sparkles")
                                .font(.system(size: 12))
                                .foregroundStyle(hasChanges ? DSColor.accentPrimary : DSColor.textMuted)
                                .frame(width: 20, height: 20)
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(!hasChanges || isGenerating || isCommitting)
                    .help("Generate commit message with AI")
                }

                // Char count (only when typing)
                if !summaryText.isEmpty {
                    Text("\(charCount)/72")
                        .font(.system(size: 9))
                        .foregroundStyle(isOverLimit ? DSColor.gitDeleted : DSColor.textMuted)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.top, 2)
                }

                Rectangle()
                    .fill(DSColor.borderSubtle)
                    .frame(height: 1)
                    .padding(.vertical, DSSpacing.xs)

                // Description
                TextField(
                    "Description (optional)",
                    text: Binding(
                        get: { gitSidebarVM.commitDescriptions[project.id] ?? "" },
                        set: { gitSidebarVM.commitDescriptions[project.id] = $0 }
                    ),
                    axis: .vertical
                )
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textSecondary)
                .textFieldStyle(.plain)
                .lineLimit(1...5)
                .disabled(!hasChanges || isCommitting)
                .opacity(hasChanges ? 1 : 0.35)
            }
            .padding(DSSpacing.sm)
            .background(DSColor.surfaceInput)
            .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: DSRadius.md)
                    .stroke(DSColor.borderDefault, lineWidth: 1)
            )

            // Inline error
            if let error = gitSidebarVM.commitPanelErrors[project.id] {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .lineLimit(2)
            }

            // Commit button
            Button {
                Task { await gitSidebarVM.performCommit(for: project) }
            } label: {
                ZStack {
                    if isCommitting {
                        ProgressView().scaleEffect(0.65)
                    } else {
                        HStack(spacing: DSSpacing.xs) {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 11))
                            Text("Commit All Changes")
                                .font(DSFont.buttonLabel)
                        }
                    }
                }
                .foregroundStyle(hasChanges ? DSColor.buttonPrimaryText : DSColor.textMuted.opacity(0.5))
                .frame(maxWidth: .infinity)
                .frame(height: DSLayout.gitButtonHeight)
                .background(
                    hasChanges
                        ? DSColor.buttonPrimaryBg.opacity(isCommitting ? 0.6 : 1.0)
                        : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
            }
            .buttonStyle(.plain)
            .disabled(!hasChanges || isCommitting)
        }
        .padding(.leading, DSSpacing.md)
        .padding(.trailing, DSSpacing.xs)
        .padding(.bottom, DSSpacing.sm)
    }
}
