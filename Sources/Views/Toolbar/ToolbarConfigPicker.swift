// MARK: - ToolbarConfigPicker
// AI-assistant selector popover for the regular-mode toolbar.
// macOS 14+, Swift 5.10

import SwiftUI

/// Dropdown-style picker that shows all `AIAssistant` cases with their install
/// / API-key status.
///
/// Tapping a "Not installed" row dispatches `agentToInstall` to the navigation
/// coordinator so the install sheet opens. Tapping any other row selects the
/// assistant via `model.selectAssistant`.
///
/// This view is purely presentational — all state lives in `ToolbarViewModel`
/// (selected assistant, isRunning, availability) and
/// `AppNavigationCoordinator` (agent-install routing).
struct ToolbarConfigPicker: View {

    @Environment(AppNavigationCoordinator.self) private var navigationCoordinator

    let model: ToolbarViewModel
    @Binding var showingPicker: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(AIAssistant.allCases) { assistant in
                row(for: assistant)
            }
        }
        .frame(minWidth: DSLayout.popoverMinWidth)
        .padding(.vertical, DSSpacing.xs)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - Row

    @ViewBuilder
    private func row(for assistant: AIAssistant) -> some View {
        let status = model.statusForAssistant(assistant)
        let canLaunch = model.agentAvailability.canLaunch(assistant)
        let isNotInstalled: Bool = {
            if case .notInstalled = status { return true }
            return false
        }()

        Button {
            if isNotInstalled {
                showingPicker = false
                navigationCoordinator.agentToInstall = assistant
            } else {
                model.selectAssistant(assistant)
                showingPicker = false
            }
        } label: {
            HStack(spacing: DSSpacing.sm) {
                AIAssistantIconView(assistant: assistant, size: 14)
                    .opacity(canLaunch ? 1.0 : 0.5)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text(assistant.displayName)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(canLaunch ? DSColor.textPrimary : DSColor.textSecondary)

                    if isNotInstalled {
                        Text("Tap to install")
                            .font(DSFont.iconMD)
                            .foregroundStyle(DSColor.accentPrimary)
                            .lineLimit(1)
                    } else if case .available(_, let hasAPIKey) = status, !hasAPIKey,
                              assistant.apiKeyEnvironmentVariable != nil {
                        Text("API key not set")
                            .font(DSFont.iconMD)
                            .foregroundStyle(DSColor.indicatorWaiting)
                            .lineLimit(1)
                    }
                }

                Spacer()

                trailingIcon(
                    isNotInstalled: isNotInstalled,
                    canLaunch: canLaunch,
                    isCurrent: assistant == model.currentAssistant
                )
            }
            .padding(.horizontal, DSSpacing.md)
            .padding(.vertical, DSSpacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func trailingIcon(isNotInstalled: Bool, canLaunch: Bool, isCurrent: Bool) -> some View {
        if isNotInstalled {
            Image(systemName: "arrow.down.circle")
                .font(DSFont.iconLG)
                .foregroundStyle(DSColor.accentPrimary)
        } else if !canLaunch {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(DSFont.iconMD)
                .foregroundStyle(DSColor.indicatorWaiting)
        } else if isCurrent {
            Image(systemName: "checkmark")
                .font(DSFont.menuCheckmark)
                .foregroundStyle(DSColor.accentPrimary)
        }
    }
}
