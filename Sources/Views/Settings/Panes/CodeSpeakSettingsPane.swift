// MARK: - CodeSpeakSettingsPane
// Settings pane for the CodeSpeak spec-driven AI coding tool.
// macOS 14+, Swift 5.10

import SwiftUI

/// Settings pane for CodeSpeak.
///
/// Shows installation status, behaviour preferences, and API key info.
struct CodeSpeakSettingsPane: View {

    @Environment(\.csPreferences) private var csPreferences

    @State private var isInstalled: Bool = false
    @State private var hasAPIKey: Bool = false

    var body: some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: DSSpacing.xl) {
                Text("CodeSpeak")
                    .font(DSFont.settingsTitle)
                    .foregroundStyle(DSColor.textPrimary)

                Divider().background(DSColor.borderDefault)

                statusSection
                behaviourSection
                displaySection
                notificationsSection
                apiKeySection

                if !isInstalled {
                    installSection
                }
            }
            .padding(DSSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .topLeading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task {
            isInstalled = CLIAgentPathResolver.resolve("codespeak") != nil
            hasAPIKey = resolveHasAuth()
        }
    }

    // MARK: - Auth Resolution

    /// Checks both codespeak OAuth token and ANTHROPIC_API_KEY (Keychain + process env).
    /// CodeSpeak can authenticate via `codespeak login` (OAuth) OR via raw API key —
    /// we show "configured" if either is present.
    private func resolveHasAuth() -> Bool {
        if hasCodeSpeakToken() { return true }
        if let key = KeychainHelper.load(account: "ANTHROPIC_API_KEY"), !key.isEmpty { return true }
        if let key = ProcessInfo.processInfo.environment["ANTHROPIC_API_KEY"], !key.isEmpty { return true }
        return false
    }

    /// Returns true if `~/.codespeak/token.json` contains at least one access token.
    private func hasCodeSpeakToken() -> Bool {
        let url = URL(fileURLWithPath: NSHomeDirectory())
            .appending(path: ".codespeak/token.json")
        guard let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tokens = json["tokens"] as? [[String: Any]] else { return false }
        return tokens.contains { ($0["access_token"] as? String)?.isEmpty == false }
    }

    // MARK: - Status

    private var statusSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Status")

            VStack(spacing: 0) {
                statusRow(
                    ok: isInstalled,
                    okText: "codespeak installed",
                    failText: "codespeak not found"
                )
                Divider()
                    .background(DSColor.borderSubtle)
                    .padding(.horizontal, DSSpacing.md)
                statusRow(
                    ok: hasAPIKey,
                    okText: "Authenticated",
                    failText: "Not authenticated (run codespeak login)",
                    failColor: DSColor.gitModified
                )
            }
            .settingsCard()
        }
    }

    // MARK: - Behaviour

    private var behaviourSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Behaviour")

            VStack(alignment: .leading, spacing: 0) {
                preferenceRow(
                    title: "Auto-build on save",
                    description: "Run `codespeak build` automatically after saving a spec file",
                    isOn: Binding(
                        get: { csPreferences.autoBuildOnSave },
                        set: { csPreferences.autoBuildOnSave = $0 }
                    )
                )

                Divider().padding(.leading, DSSpacing.md)

                preferenceRow(
                    title: "Build on project open",
                    description: "Run `codespeak build` when switching to a CodeSpeak project",
                    isOn: Binding(
                        get: { csPreferences.buildOnProjectOpen },
                        set: { csPreferences.buildOnProjectOpen = $0 }
                    )
                )

                Divider().padding(.leading, DSSpacing.md)

                preferenceRow(
                    title: "Auto-open build panel",
                    description: "Open the build panel automatically when a command starts",
                    isOn: Binding(
                        get: { csPreferences.autoOpenBuildPanel },
                        set: { csPreferences.autoOpenBuildPanel = $0 }
                    )
                )

                Divider().padding(.leading, DSSpacing.md)

                defaultCommandRow
            }
            .settingsCard()
        }
    }

    // MARK: - Default Command Row

    private var defaultCommandRow: some View {
        HStack(spacing: DSSpacing.sm) {
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text("Default command")
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.textPrimary)
                Text("Command executed by the ▶ button")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
            }

            Spacer()

            Picker("", selection: Binding(
                get: { csPreferences.defaultCommand },
                set: { csPreferences.defaultCommand = $0 }
            )) {
                // .task and .change require text input — exclude from silent defaults
                ForEach(CodeSpeakCommand.allCases.filter { !$0.requiresInput }) { cmd in
                    Text(cmd.displayName).tag(cmd)
                }
            }
            .pickerStyle(.menu)
            .frame(width: 90)
            .labelsHidden()
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }

    // MARK: - Display

    private var displaySection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Display")

            VStack(alignment: .leading, spacing: 0) {
                preferenceRow(
                    title: "Show failing specs only",
                    description: "Filter the spec list to show only specs with failing status",
                    isOn: Binding(
                        get: { csPreferences.showFailingOnly },
                        set: { csPreferences.showFailingOnly = $0 }
                    )
                )
            }
            .settingsCard()
        }
    }

    // MARK: - Notifications

    private var notificationsSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Notifications")

            VStack(alignment: .leading, spacing: 0) {
                preferenceRow(
                    title: "Notify on complete",
                    description: "Send a macOS notification when a command finishes",
                    isOn: Binding(
                        get: { csPreferences.notifyOnComplete },
                        set: { csPreferences.notifyOnComplete = $0 }
                    )
                )
            }
            .settingsCard()
        }
    }

    // MARK: - API Key

    private var apiKeySection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "API Key")
            Text("Authenticate via `codespeak login` (OAuth) or set ANTHROPIC_API_KEY in Settings → Claude.")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(DSSpacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .settingsCard()
        }
    }

    // MARK: - Installation

    private var installSection: some View {
        VStack(alignment: .leading, spacing: DSSpacing.sm) {
            SettingsSectionHeader(title: "Installation")

            Text("""
                1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh
                2. uv tool install codespeak-cli
                3. codespeak init  (in your project)
                """)
                .font(DSFont.terminal(size: 12))
                .foregroundStyle(DSColor.textPrimary)
                .padding(DSSpacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .settingsCard()
        }
    }

    // MARK: - Helpers

    private func statusRow(
        ok: Bool,
        okText: String,
        failText: String,
        failColor: Color = DSColor.gitDeleted
    ) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Circle()
                .fill(ok ? DSColor.gitAdded : failColor)
                .frame(width: DSLayout.statusDotSize, height: DSLayout.statusDotSize)
            Text(ok ? okText : failText)
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textPrimary)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }

    private func preferenceRow(
        title: String,
        description: String,
        isOn: Binding<Bool>
    ) -> some View {
        HStack(spacing: DSSpacing.sm) {
            VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                Text(title)
                    .font(DSFont.buttonLabel)
                    .foregroundStyle(DSColor.textPrimary)
                Text(description)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()

            Toggle("", isOn: isOn)
                .toggleStyle(.switch)
                .labelsHidden()
                .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }
}
