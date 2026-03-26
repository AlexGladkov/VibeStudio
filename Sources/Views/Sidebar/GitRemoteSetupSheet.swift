// MARK: - GitRemoteSetupSheet
// Modal sheet for adding a git remote to a project.
// macOS 14+, Swift 5.10

import SwiftUI

struct GitRemoteSetupSheet: View {

    let project: Project

    @Environment(\.gitService) private var gitService
    @Environment(\.dismiss) private var dismiss

    @State private var vm: GitRemoteSetupViewModel?

    private var viewModel: GitRemoteSetupViewModel {
        if let existing = vm { return existing }
        let created = GitRemoteSetupViewModel(gitService: gitService, project: project)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
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
                TextField("origin", text: Binding(
                    get: { model.remoteName },
                    set: { model.remoteName = $0 }
                ))
                .styledInput()
            }

            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Repository URL")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("https://github.com/user/repo.git", text: Binding(
                    get: { model.remoteUrl },
                    set: { model.remoteUrl = $0 }
                ))
                .styledInput()
            }

            if let error = model.errorMessage {
                Text(error).font(DSFont.sidebarItemSmall).foregroundStyle(DSColor.gitDeleted)
            }
            if let success = model.successMessage {
                Text(success).font(DSFont.sidebarItemSmall).foregroundStyle(DSColor.gitAdded)
            }

            Spacer()

            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Add Remote",
                isDisabled: !model.canAdd,
                isLoading: model.isAdding,
                onAction: {
                    Task {
                        if await model.addRemote() {
                            dismiss()
                        }
                    }
                }
            )
        }
        .padding(DSSpacing.lg)
        .frame(width: 380, height: 320)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            if vm == nil {
                vm = GitRemoteSetupViewModel(gitService: gitService, project: project)
            }
        }
    }
}
