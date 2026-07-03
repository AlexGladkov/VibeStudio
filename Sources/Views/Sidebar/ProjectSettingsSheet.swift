// MARK: - ProjectSettingsSheet
// Per-project settings modal: production URL and future config options.
// macOS 14+, Swift 5.10

import SwiftUI

struct ProjectSettingsSheet: View {

    let project: Project

    @Environment(\.projectManager) private var projectManager
    @Environment(\.dismiss) private var dismiss

    @State private var vmBox = LazyStateObject<ProjectSettingsViewModel>()

    private var viewModel: ProjectSettingsViewModel {
        vmBox.resolve { ProjectSettingsViewModel(projectManager: projectManager, project: project) }
    }

    var body: some View {
        let model = viewModel
        VStack(alignment: .leading, spacing: DSSpacing.lg) {
            // Header
            VStack(alignment: .leading, spacing: DSSpacing.xs) {
                Text("Project Settings")
                    .font(DSFont.sheetTitle)
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
                    .font(DSFont.iconMD)
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
        .frame(
            minWidth: DSLayout.sheetSmallWidth,
            idealWidth: DSLayout.sheetMediumWidth - 20,
            minHeight: DSLayout.sheetSmallHeight,
            idealHeight: DSLayout.sheetMediumHeight - 60
        )
        .background(DSColor.surfaceOverlay)
    }
}
