// MARK: - CreateBranchSheet
// Modal sheet for creating a new git branch.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - CreateBranchSheet

struct CreateBranchSheet: View {

    let project: Project
    var fromBranch: String? = nil
    var onCreated: (() -> Void)?

    @Environment(\.gitService) private var gitService
    @Environment(\.dismiss) private var dismiss

    @State private var branchName: String = ""
    @State private var isCreating = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.lg) {
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("New Branch")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                if let from = fromBranch {
                    HStack(spacing: 4) {
                        Text("from")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                        Image(systemName: "arrow.triangle.branch")
                            .font(.system(size: 10))
                            .foregroundStyle(DSColor.accentPrimary)
                        Text(from)
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.accentPrimary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                } else {
                    Text(project.name)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                }
            }

            Divider()
                .background(DSColor.borderDefault)

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Branch name")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("feature/my-feature", text: $branchName)
                    .styledInput()
                    .onSubmit { Task { await create() } }
            }

            if let error = errorMessage {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .lineLimit(2)
            }

            Spacer()

            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Create",
                isDisabled: branchName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                isLoading: isCreating,
                onAction: { Task { await create() } }
            )
        }
        .padding(DSSpacing.lg)
        .frame(width: 320, height: 240)
        .background(DSColor.surfaceOverlay)
    }

    private func create() async {
        let name = branchName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        isCreating = true
        errorMessage = nil
        do {
            try await gitService.createBranch(name: name, from: fromBranch, at: project.path)
            dismiss()
            onCreated?()
        } catch {
            errorMessage = error.localizedDescription
            isCreating = false
        }
    }
}
