// MARK: - GitRemoteSetupSheet
// Modal sheet for adding a git remote to a project.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - GitRemoteSetupSheet

struct GitRemoteSetupSheet: View {

    let project: Project

    @Environment(\.gitService) private var gitService
    @Environment(\.dismiss) private var dismiss

    @State private var remoteName = "origin"
    @State private var remoteUrl = ""
    @State private var isAdding = false
    @State private var errorMessage: String?
    @State private var successMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.lg) {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Remote Repository")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                Text(project.name)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

            Divider()
                .background(DSColor.borderDefault)

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Remote name")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("origin", text: $remoteName)
                    .styledInput()
            }

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Repository URL")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("https://github.com/user/repo.git", text: $remoteUrl)
                    .styledInput()
            }

            if let error = errorMessage {
                Text(error).font(DSFont.sidebarItemSmall).foregroundStyle(DSColor.gitDeleted)
            }
            if let success = successMessage {
                Text(success).font(DSFont.sidebarItemSmall).foregroundStyle(DSColor.gitAdded)
            }

            Spacer()

            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Add Remote",
                isDisabled: remoteUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                isLoading: isAdding,
                onAction: { Task { await addRemote() } }
            )
        }
        .padding(DSSpacing.lg)
        .frame(width: 380, height: 320)
        .background(DSColor.surfaceOverlay)
    }

    private func addRemote() async {
        let url = remoteUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedName = remoteName.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = trimmedName.isEmpty ? "origin" : trimmedName
        guard !url.isEmpty else { return }

        isAdding = true
        errorMessage = nil
        successMessage = nil
        defer { isAdding = false }

        do {
            try await gitService.addRemote(name: name, url: url, at: project.path)
            successMessage = "Remote '\(name)' added"
            try? await Task.sleep(for: .milliseconds(800))
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
