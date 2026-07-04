// MARK: - PiSettingsPane
// Settings pane for the Pi coding agent (pi.dev).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - PiSettingsPane

/// Settings pane for Pi (pi.dev).
///
/// Pi is provider-agnostic: authentication (API keys or OAuth) and model
/// selection are managed by the `pi` CLI itself (via `pi` prompts / `models.json`),
/// so there is no in-app config file to edit — this pane just documents how to
/// authenticate.
struct PiSettingsPane: View {

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("Pi")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                versionSection

                Text("Provider-agnostic AI coding agent. Supports Anthropic, OpenAI, "
                     + "Google, and 15+ other providers via API keys or OAuth.")
                    .font(DSFont.bodyMedium)
                    .foregroundStyle(DSColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                authInfoRow
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Version

    private var versionSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Version")
            AgentVersionRow(assistant: .pi)
                .settingsCard()
        }
    }

    // MARK: - Auth Info Row

    private var authInfoRow: some View {
        SettingsAuthInfoRow(
            hint: "Providers and API keys are managed via the CLI: ",
            command: "pi"
        )
    }
}
