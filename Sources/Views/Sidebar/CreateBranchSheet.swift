// MARK: - CreateBranchSheet
// Modal sheet for creating a new git branch.
// macOS 14+, Swift 5.10

import SwiftUI

struct CreateBranchSheet: View {

    let project: Project
    var fromBranch: String? = nil
    var onCreated: (() -> Void)?

    @Environment(\.gitService) private var gitService
    @Environment(\.dismiss) private var dismiss

    @State private var vm: CreateBranchViewModel?

    private var viewModel: CreateBranchViewModel {
        if let existing = vm { return existing }
        let created = CreateBranchViewModel(
            gitService: gitService,
            project: project,
            fromBranch: fromBranch
        )
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
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
                TextField("feature/my-feature", text: Binding(
                    get: { model.branchName },
                    set: { model.branchName = $0 }
                ))
                .styledInput()
                .onSubmit {
                    Task {
                        if await model.create() {
                            dismiss()
                            onCreated?()
                        }
                    }
                }
            }

            if let error = model.errorMessage {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .lineLimit(2)
            }

            Spacer()

            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Create",
                isDisabled: !model.canCreate,
                isLoading: model.isCreating,
                onAction: {
                    Task {
                        if await model.create() {
                            dismiss()
                            onCreated?()
                        }
                    }
                }
            )
        }
        .padding(DSSpacing.lg)
        .frame(width: 320, height: 240)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            if vm == nil {
                vm = CreateBranchViewModel(
                    gitService: gitService,
                    project: project,
                    fromBranch: fromBranch
                )
            }
        }
    }
}
