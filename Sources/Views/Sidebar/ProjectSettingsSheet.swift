// MARK: - ProjectSettingsSheet
// Per-project settings modal: production URL and future config options.
// macOS 14+, Swift 5.10

import SwiftUI

struct ProjectSettingsSheet: View {

    let project: Project

    @Environment(\.projectManager) private var projectManager
    @Environment(\.dismiss) private var dismiss

    @State private var productionURL: String = ""

    var body: some View {
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
                TextField("https://example.com", text: $productionURL)
                    .styledInput()
                    .onSubmit { saveSettings() }
                Text("Used for the \"Open in Browser\" toolbar action")
                    .font(.system(size: 10))
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()

            // Action buttons
            SheetActionButtons(
                onCancel: { dismiss() },
                actionLabel: "Save",
                onAction: { saveSettings() }
            )
            .keyboardShortcut(.return, modifiers: .command)
        }
        .padding(DSSpacing.lg)
        .frame(width: 360, height: 260)
        .background(DSColor.surfaceOverlay)
        .onAppear {
            productionURL = project.productionURL ?? ""
        }
    }

    private func saveSettings() {
        var trimmed = productionURL.trimmingCharacters(in: .whitespacesAndNewlines)
        // Auto-prepend https:// if user forgot the scheme
        if !trimmed.isEmpty, !trimmed.hasPrefix("http://"), !trimmed.hasPrefix("https://") {
            trimmed = "https://" + trimmed
        }
        try? projectManager.updateProject(project.id) {
            $0.productionURL = trimmed.isEmpty ? nil : trimmed
        }
        dismiss()
    }
}
