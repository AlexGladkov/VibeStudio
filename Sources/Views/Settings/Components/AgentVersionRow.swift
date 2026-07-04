// MARK: - AgentVersionRow
// Settings row showing an agent's installed version with an Update button.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - AgentVersionRow

/// A settings card row that displays the installed CLI version of an AI agent
/// and provides an Update button that runs the agent's update command in a
/// background login shell.
///
/// The row triggers a version check via ``AgentVersionChecking/refresh(_:)``
/// when it first appears, shows a spinner while checking or updating, and
/// surfaces a coloured result message after an update attempt.
///
/// Usage:
/// ```swift
/// AgentVersionRow(assistant: .claude)
///     .settingsCard()
/// ```
struct AgentVersionRow: View {

    // MARK: Properties

    /// The agent whose version this row displays.
    let assistant: AIAssistant

    @Environment(\.agentVersion) private var versionService

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            mainRow

            if let message = versionService.lastMessage(for: assistant) {
                Text(message)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(resultColor(for: message))
                    .padding(.horizontal, DSSpacing.md)
                    .padding(.bottom, DSSpacing.sm)
            }
        }
        .task {
            versionService.refresh(assistant)
        }
    }

    // MARK: - Main Row

    private var mainRow: some View {
        HStack(spacing: DSSpacing.sm) {
            Text("Version")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textPrimary)

            versionValueView

            Spacer()

            if assistant.updateCommand != nil {
                if versionService.isUpdating(assistant) {
                    ProgressView()
                        .controlSize(.small)
                        .frame(
                            width: DSLayout.sidebarActionButtonSize,
                            height: DSLayout.sidebarActionButtonSize
                        )
                } else {
                    Button("Update") {
                        versionService.update(assistant)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.small)
                }
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .padding(.vertical, DSSpacing.sm)
    }

    // MARK: - Version Value

    @ViewBuilder
    private var versionValueView: some View {
        switch versionService.versionState(for: assistant) {
        case .installed(let version):
            Text("v\(version)")
                .font(DSFont.monoPath)
                .foregroundStyle(DSColor.textSecondary)
        case .notInstalled:
            Text("Not installed")
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textMuted)
        case .checking, .unknown:
            ProgressView()
                .controlSize(.mini)
        }
    }

    // MARK: - Helpers

    /// Green for a successful update message, red for failures.
    private func resultColor(for message: String) -> Color {
        message == String(localized: "Updated") ? DSColor.gitAdded : DSColor.gitDeleted
    }
}
