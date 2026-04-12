// MARK: - ToolbarView
// Android Studio–style run-configuration bar above the tab bar.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI

// MARK: - ToolbarView

/// Android Studio–style run-configuration toolbar above the tab bar.
///
/// Layout: `[ 🤖 claude ▾ ]  [ ▶ / ■ ]  ···`
struct ToolbarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.agentAvailability) private var agentAvailability
    @Environment(\.navigationCoordinator) private var navigationCoordinator
    @Environment(\.openURL) private var openURL

    @State private var vm: ToolbarViewModel?
    @State private var showingPicker = false

    private var viewModel: ToolbarViewModel {
        if let existing = vm { return existing }
        let created = ToolbarViewModel(
            projectManager: projectManager,
            terminalManager: terminalManager,
            agentAvailability: agentAvailability
        )
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        HStack(spacing: 6) {
            configPicker(model: model)
            playStopButton(model: model)
            openInBrowserButton(model: model)
            settingsButton()
            changesToggleButton()
        }
        .padding(.horizontal, 12)
        .onAppear {
            if vm == nil {
                vm = ToolbarViewModel(
                    projectManager: projectManager,
                    terminalManager: terminalManager,
                    agentAvailability: agentAvailability
                )
            }
        }
    }

    // MARK: - Configuration Picker

    private func configPicker(model: ToolbarViewModel) -> some View {
        Button {
            guard !model.isRunning, model.activeId != nil else { return }
            showingPicker.toggle()
        } label: {
            HStack(spacing: 5) {
                AIAssistantIconView(assistant: model.currentAssistant, size: 14)
                    .opacity(model.isRunning ? 0.4 : 1.0)

                Text(model.currentAssistant.displayName)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(model.isRunning ? DSColor.textMuted : DSColor.textPrimary)

                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(model.isRunning ? DSColor.textMuted : DSColor.textSecondary)
            }
            .frame(height: 22)
        }
        .buttonStyle(.plain)
        .disabled(model.isRunning || model.activeId == nil)
        .popover(isPresented: $showingPicker, arrowEdge: .bottom) {
            pickerPopover(model: model)
        }
    }

    private func pickerPopover(model: ToolbarViewModel) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(AIAssistant.allCases) { assistant in
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
                    HStack(spacing: 8) {
                        AIAssistantIconView(assistant: assistant, size: 14)
                            .opacity(canLaunch ? 1.0 : 0.5)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(assistant.displayName)
                                .font(.system(size: 13))
                                .foregroundStyle(canLaunch ? DSColor.textPrimary : DSColor.textSecondary)

                            if isNotInstalled {
                                Text("Нажми для установки")
                                    .font(.system(size: 10))
                                    .foregroundStyle(DSColor.accentPrimary)
                                    .lineLimit(1)
                            } else if case .available(_, let hasAPIKey) = status, !hasAPIKey,
                                      assistant.apiKeyEnvironmentVariable != nil {
                                Text("API key not set")
                                    .font(.system(size: 10))
                                    .foregroundStyle(DSColor.indicatorWaiting)
                                    .lineLimit(1)
                            }
                        }

                        Spacer()

                        if isNotInstalled {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: 14))
                                .foregroundStyle(DSColor.accentPrimary)
                        } else if !canLaunch {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(DSColor.indicatorWaiting)
                        } else if assistant == model.currentAssistant {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(DSColor.accentPrimary)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(minWidth: 200)
        .padding(.vertical, 4)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - Play / Stop Button

    private func playStopButton(model: ToolbarViewModel) -> some View {
        Button {
            if model.isRunning { model.stopAssistant() } else { model.startAssistant() }
        } label: {
            Group {
                if model.isRunning {
                    Image(systemName: "stop.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(DSColor.actionStop)
                } else {
                    Image(systemName: "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(
                            model.activeId == nil ? DSColor.textMuted : DSColor.actionRun
                        )
                }
            }
            .frame(width: 26, height: 22)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.activeId == nil)
    }

    // MARK: - Changes Panel Toggle

    private func changesToggleButton() -> some View {
        Button {
            withAnimation(.easeOut(duration: 0.2)) {
                navigationCoordinator.showingChangesPanel.toggle()
            }
        } label: {
            Image(systemName: "sidebar.right")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(
                    navigationCoordinator.showingChangesPanel
                        ? DSColor.accentPrimary
                        : DSColor.textSecondary
                )
                .frame(width: 26, height: 22)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help("Toggle Changes Panel (\u{2318}\u{21E7}G)")
    }

    // MARK: - Settings Button

    private func settingsButton() -> some View {
        Button {
            navigationCoordinator.showingSettings = true
        } label: {
            Image(systemName: "gear")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)
                .frame(width: 26, height: 22)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help("Settings")
    }

    // MARK: - Open in Browser Button

    private func openInBrowserButton(model: ToolbarViewModel) -> some View {
        Button {
            if let url = model.activeProductionURL {
                openURL(url)
            }
        } label: {
            Image(systemName: "globe")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(model.activeProductionURL != nil ? DSColor.textPrimary : DSColor.textMuted)
                .frame(width: 26, height: 22)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.activeProductionURL == nil)
        .help(model.activeProductionURL.map { "Open \($0.absoluteString) in browser" } ?? "No production URL set")
    }
}

