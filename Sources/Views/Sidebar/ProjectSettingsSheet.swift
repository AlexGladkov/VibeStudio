// MARK: - ProjectSettingsSheet
// Per-project settings modal: production URL and future config options.
// macOS 14+, Swift 5.10

import SwiftUI

struct ProjectSettingsSheet: View {

    let project: Project

    @Environment(\.projectManager) private var projectManager
    @Environment(\.dismiss) private var dismiss

    @State private var vm: ProjectSettingsViewModel?

    private var viewModel: ProjectSettingsViewModel {
        if let existing = vm { return existing }
        let created = ProjectSettingsViewModel(projectManager: projectManager, project: project)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        VStack(alignment: .leading, spacing: DSSpacing.lg) {
            // Header
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Project Settings")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                Text(project.name)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

            Divider()
                .background(DSColor.borderDefault)

            // Production URL field
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Production URL")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                TextField("https://example.com", text: Binding(
                    get: { model.productionURL },
                    set: { model.productionURL = $0 }
                ))
                .styledInput()
                .onSubmit {
                    if model.saveSettings() { dismiss() }
                }
                Text("Used for the \"Open in Browser\" toolbar action")
                    .font(.system(size: 10))
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()

            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Save",
                onAction: {
                    if model.saveSettings() { dismiss() }
                }
            )
            .keyboardShortcut(.return, modifiers: .command)
        }
        .padding(DSSpacing.lg)
        .frame(width: 360, height: 260)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            if vm == nil {
                vm = ProjectSettingsViewModel(projectManager: projectManager, project: project)
            }
        }
    }
}
