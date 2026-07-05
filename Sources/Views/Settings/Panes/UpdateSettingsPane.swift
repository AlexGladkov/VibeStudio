// MARK: - UpdateSettingsPane
// Software update settings for the Settings window.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - UpdateSettingsPane

/// Software update settings pane.
///
/// Surfaces the current app version, an automatic-check toggle (persisted by
/// Sparkle), and a manual "Check Now" action. Mirrors the layout conventions
/// of ``GeneralSettingsPane`` (DSFont / DSColor / DSLayout tokens).
struct UpdateSettingsPane: View {

    @Environment(\.updateService) private var updateService

    /// Local mirror of Sparkle's automatic-check preference so the toggle
    /// re-renders immediately (the service is a plain protocol existential,
    /// not `@Observable`).
    @State private var automaticChecks: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Updates")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                // Current version
                HStack(spacing: DSSpacing.lg) {
                    Text("Current version")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)
                        .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                    Text(Bundle.main.appVersionDisplay)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textSecondary)
                        .monospacedDigit()
                        .textSelection(.enabled)

                    Spacer()
                }

                // Automatic checks toggle
                HStack(spacing: DSSpacing.lg) {
                    Text("Automatic updates")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)
                        .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                    Toggle("", isOn: Binding(
                        get: { automaticChecks },
                        set: {
                            automaticChecks = $0
                            updateService.automaticallyChecksForUpdates = $0
                        }
                    ))
                    .toggleStyle(.switch)
                    .labelsHidden()

                    Text("Check for updates in the background")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)

                    Spacer()
                }

                // Manual check
                HStack(spacing: DSSpacing.lg) {
                    Text("")
                        .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                    Button("Check for Updates…") {
                        updateService.checkForUpdates()
                    }
                    .disabled(!updateService.canCheckForUpdates)

                    Spacer()
                }
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            automaticChecks = updateService.automaticallyChecksForUpdates
        }
    }
}
