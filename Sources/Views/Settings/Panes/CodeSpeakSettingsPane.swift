// MARK: - CodeSpeakSettingsPane
// Settings pane for the CodeSpeak spec-driven AI coding tool.
// macOS 14+, Swift 5.10

import SwiftUI

/// Settings pane for CodeSpeak.
///
/// Shows installation status, API key info (shared with Claude settings),
/// and links to CodeSpeak documentation.
struct CodeSpeakSettingsPane: View {

    @State private var isInstalled: Bool = false
    @State private var hasAPIKey: Bool = false

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("CodeSpeak")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                // Status section
                VStack(alignment: .leading, spacing: DSSpacing.sm) {
                    Text("STATUS")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)

                    HStack(spacing: DSSpacing.xs) {
                        Circle()
                            .fill(isInstalled ? DSColor.gitAdded : DSColor.gitDeleted)
                            .frame(width: DSLayout.statusDotSize, height: DSLayout.statusDotSize)
                        Text(isInstalled ? "codespeak installed" : "codespeak not found")
                            .font(DSFont.sidebarItem)
                            .foregroundStyle(DSColor.textPrimary)
                    }

                    HStack(spacing: DSSpacing.xs) {
                        Circle()
                            .fill(hasAPIKey ? DSColor.gitAdded : DSColor.gitModified)
                            .frame(width: DSLayout.statusDotSize, height: DSLayout.statusDotSize)
                        Text(hasAPIKey ? "ANTHROPIC_API_KEY configured" : "ANTHROPIC_API_KEY not set")
                            .font(DSFont.sidebarItem)
                            .foregroundStyle(DSColor.textPrimary)
                    }
                }
                .padding(DSSpacing.md)
                .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))

                // Installation instructions
                if !isInstalled {
                    VStack(alignment: .leading, spacing: DSSpacing.sm) {
                        Text("INSTALLATION")
                            .font(DSFont.sidebarSection)
                            .foregroundStyle(DSColor.textSecondary)

                        Text("""
                            1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh
                            2. uv tool install codespeak-cli
                            3. codespeak init  (in your project)
                            """)
                            .font(DSFont.terminal(size: 12))
                            .foregroundStyle(DSColor.textPrimary)
                            .padding(DSSpacing.md)
                            .background(DSColor.surfaceInput, in: RoundedRectangle(cornerRadius: DSRadius.md))
                    }
                }

                // API key note
                VStack(alignment: .leading, spacing: DSSpacing.xs) {
                    Text("API KEY")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)
                    Text("CodeSpeak uses your ANTHROPIC_API_KEY. Set it in Settings → Claude.")
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(DSSpacing.md)
                .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            isInstalled = CLIAgentPathResolver.resolve("codespeak") != nil
            let key = KeychainHelper.load(account: "ANTHROPIC_API_KEY") ?? ""
            hasAPIKey = !key.isEmpty
        }
    }
}
