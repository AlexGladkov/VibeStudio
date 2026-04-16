// MARK: - GeneralSettingsPane
// Appearance settings for the Settings window.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - GeneralSettingsPane

/// Appearance settings pane.
///
/// Reads and writes the selected theme via ``ThemeService``.
/// Changes take effect immediately — `NSApp.appearance` is set synchronously
/// so the whole app (including AppKit views) switches without relaunch.
struct GeneralSettingsPane: View {

    @Environment(\.themeService) private var themeService

    var body: some View {
        VStack(alignment: .leading, spacing: DSSpacing.xl) {
            Text("Внешний вид")
                .font(DSFont.settingsTitle)
                .foregroundStyle(DSColor.textPrimary)

            Divider().background(DSColor.borderDefault)

            HStack(spacing: DSSpacing.lg) {
                Text("Тема")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.textPrimary)
                    .frame(width: DSLayout.settingsLabelWidth, alignment: .leading)

                Picker("", selection: Binding(
                    get: { themeService.selectedAppearance.rawValue },
                    set: { themeService.setAppearance(AppAppearance(rawValue: $0) ?? .system) }
                )) {
                    Text("System").tag(0)
                    Text("Dark").tag(1)
                    Text("Light").tag(2)
                }
                .pickerStyle(.segmented)
                .frame(width: DSLayout.settingsPickerWidth)
                .labelsHidden()

                Spacer()
            }

            Spacer()
        }
        .padding(DSSpacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
