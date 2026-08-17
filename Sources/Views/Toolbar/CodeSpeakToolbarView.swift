// MARK: - CodeSpeakToolbarView
// Three-section CodeSpeak toolbar: breadcrumb (left of editor) + stats badge + run bar.
// Strictly follows the CLAUDE.md CodeSpeak titlebar invariant:
//   • breadcrumb pinned to the LEFT EDGE of the centre column,
//   • controls pinned to the RIGHT of the titlebar.
// macOS 14+, Swift 5.10

import SwiftUI

/// CodeSpeak-mode toolbar laid out as three sections inside the titlebar.
///
/// Layout (left → right):
/// 1. **Box 1** — invisible spacer that mirrors the spec sidebar width
///    minus the macOS traffic-lights area. Keeps the breadcrumb aligned with
///    the left edge of the centre editor column even when the sidebar is
///    resized.
/// 2. **Box 2** — `Projects › projectName › specName` breadcrumb plus the
///    pass/fail stats badge (e.g. `12/15`).
/// 3. **Box 3** — Command picker + optional input field + ▶/■ + QR + devices
///    + ⚙ settings.
///
/// `specsColumnWidth` is updated by `CodeSpeakModeView` via `GeometryReader`
/// on the spec list column. The traffic-lights offset is hardcoded to the
/// macOS Sonoma/Sequoia standard (`DSLayout.trafficLightsEndFallback = 84`).
struct CodeSpeakToolbarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.codeSpeak) private var codeSpeak
    @Environment(\.remoteControlServer) private var remoteServer
    @Environment(\.generalPreferences) private var generalPreferences
    @Environment(\.costTrackerService) private var costTrackerService
    @Environment(AppNavigationCoordinator.self) private var navigationCoordinator

    @State private var showingProjectPicker = false
    @State private var showingRemotePopover = false
    @State private var showingQRPopover = false

    private var activeProject: Project? { projectManager.activeProject }

    var body: some View {
        // trafficLightsEnd — the standard macOS value used in
        // WindowToolbarRemover. The hosting view's leading anchor is set to
        // this value, so subtracting it gives the offset within the hosting
        // view where the specs column ends.
        let trafficLightsEnd = DSLayout.trafficLightsEndFallback
        let specsWidth = navigationCoordinator.specsColumnWidth
        let box1Width = max(0, specsWidth - trafficLightsEnd)

        HStack(spacing: 0) {
            // Box 1 — left panel placeholder (transparent)
            Color.clear
                .frame(width: box1Width)

            // Box 2 — centre: breadcrumb pinned to left of centre column
            HStack(spacing: DSSpacing.sm) {
                breadcrumb
                    .padding(.leading, DSSpacing.sm)
                statsBadge
            }

            Spacer(minLength: DSSpacing.sm)

            // Box 3 — right: run controls
            HStack(spacing: DSSpacing.sm) {
                runBar
                costBadge
                remoteQRButton
                remoteIndicator
                settingsButton
            }
            .padding(.trailing, DSSpacing.md)
        }
    }

    // MARK: - Breadcrumb
    //
    // Static breadcrumb on the leading side of the titlebar:
    //   `Projects  ›  projectName  ›  spec.cs.md`
    // "Projects" is clickable (returns to welcome screen). Project and spec
    // names are plain text — no dropdown, one project at a time.

    private var breadcrumb: some View {
        HStack(spacing: 0) {
            Button {
                projectManager.activeProjectId = nil
            } label: {
                Text("Projects")
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("All Projects")

            if let project = activeProject {
                Text(" \u{203A} ")
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textGhost)

                Text(project.name)
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textSecondary)
                    .lineLimit(1)
            }

            if !navigationCoordinator.runBar.currentSpecName.isEmpty {
                Text(" \u{203A} ")
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textGhost)

                Text(navigationCoordinator.runBar.currentSpecName)
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textPrimary)
                    .lineLimit(1)

                // Dirty indicator
                if navigationCoordinator.runBar.isEditorDirty {
                    Circle()
                        .fill(DSColor.gitModified)
                        .frame(width: 5, height: 5) // dirty dot, intentionally sub-grid
                        .padding(.leading, DSSpacing.xxs)
                        .accessibilityHidden(true)
                }
            }
        }
    }

    // MARK: - Stats Badge

    @ViewBuilder
    private var statsBadge: some View {
        if let id = projectManager.activeProjectId,
           let stats = codeSpeak.projectStats[id] {
            StatusBadgeView(
                "\(stats.passing)/\(stats.total)",
                color: stats.allPassing ? DSColor.gitAdded : DSColor.gitModified,
                font: DSFont.smallButtonLabel,
                background: .solid(stats.allPassing ? DSColor.diffAddedBg : DSColor.diffDeletedBg)
            )
        }
    }

    // MARK: - Cost Badge

    /// Token/cost badge for the active CodeSpeak agent session.
    /// Uses `anyActiveSnapshot()` since CodeSpeak runs one agent at a time
    /// and the ToolbarViewModel is not available in this view.
    @ViewBuilder
    private var costBadge: some View {
        if generalPreferences.costTrackerEnabled && navigationCoordinator.runBar.isRunning {
            CostBadgeView(snapshot: costTrackerService.anyActiveSnapshot())
        }
    }

    // MARK: - Run Bar

    /// Returns `true` if the current command can be launched (non-empty
    /// required inputs).
    private var canRun: Bool {
        switch navigationCoordinator.runBar.command {
        case .task:   return !navigationCoordinator.runBar.taskName.trimmingCharacters(in: .whitespaces).isEmpty
        case .change: return !navigationCoordinator.runBar.changeMessage.trimmingCharacters(in: .whitespaces).isEmpty
        default:      return true
        }
    }

    /// Toolbar run bar: `[Command ▼] [optional text field] [▶/■]`.
    ///
    /// Replaces the old hardcoded `▶ Build` button. Command state lives in
    /// `AppNavigationCoordinator` so the toolbar and `CodeSpeakModeView` share
    /// the same source of truth.
    private var runBar: some View {
        HStack(spacing: DSSpacing.sm) {
            // CodeSpeak icon (moved here from the right panel header)
            Image(systemName: "doc.text.magnifyingglass")
                .font(DSFont.tabTitle)
                .foregroundStyle(DSColor.agentCodeSpeak)
                .accessibilityHidden(true)

            commandMenu
            commandInputField

            playStopButton
        }
    }

    private var commandMenu: some View {
        Menu {
            ForEach(CodeSpeakCommand.allCases) { cmd in
                Button(cmd.titleKey) {
                    navigationCoordinator.runBar.command = cmd
                }
            }
        } label: {
            HStack(spacing: DSSpacing.xs) {
                Text(navigationCoordinator.runBar.command.titleKey)
                    .font(DSFont.tabTitle)
                    .foregroundStyle(
                        navigationCoordinator.runBar.isRunning
                            ? DSColor.textMuted
                            : DSColor.textPrimary
                    )
                Image(systemName: "chevron.down")
                    .font(DSFont.iconSM)
                    .foregroundStyle(DSColor.textSecondary)
                    .accessibilityHidden(true)
            }
            .frame(height: DSLayout.toolbarButtonHeight)
        }
        .menuStyle(.borderlessButton)
        .fixedSize()
        .disabled(navigationCoordinator.runBar.isRunning)
    }

    @ViewBuilder
    private var commandInputField: some View {
        if navigationCoordinator.runBar.command == .task {
            inputField(
                placeholder: "Task name...",
                text: Binding(
                    get: { navigationCoordinator.runBar.taskName },
                    set: { navigationCoordinator.runBar.taskName = $0 }
                )
            )
        } else if navigationCoordinator.runBar.command == .change {
            inputField(
                placeholder: "Describe the change...",
                text: Binding(
                    get: { navigationCoordinator.runBar.changeMessage },
                    set: { navigationCoordinator.runBar.changeMessage = $0 }
                )
            )
        }
    }

    private func inputField(placeholder: String, text: Binding<String>) -> some View {
        TextField(placeholder, text: text)
            .textFieldStyle(.plain)
            .font(DSFont.sidebarItemSmall)
            .foregroundStyle(DSColor.textPrimary)
            .frame(minWidth: DSLayout.toolbarTextFieldMinWidth, maxWidth: DSLayout.toolbarTextFieldMaxWidth)
            .padding(.horizontal, DSSpacing.sm)
            .background(
                DSColor.surfaceInput,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
            .disabled(navigationCoordinator.runBar.isRunning)
    }

    private var playStopButton: some View {
        PlayStopButton(
            isRunning: navigationCoordinator.runBar.isRunning,
            canRun: canRun,
            size: .compact,
            runningHelp: "Stop codespeak",
            idleHelp: "Run codespeak \(navigationCoordinator.runBar.command.displayName.lowercased())"
        ) {
            if navigationCoordinator.runBar.isRunning {
                navigationCoordinator.runBar.stopRequested = true
            } else {
                navigationCoordinator.codeSpeakBuildRequested = true
            }
        }
    }

    // MARK: - Remote QR Button

    @ViewBuilder
    private var remoteQRButton: some View {
        if remoteServer.isRunning {
            Button { showingQRPopover.toggle() } label: {
                Image(systemName: "qrcode")
                    .foregroundStyle(DSColor.textSecondary)
                    .toolbarIconButton()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remote Control connection")
            .help("Connection QR code")
            .popover(isPresented: $showingQRPopover, arrowEdge: .bottom) {
                RemoteQRPopover(
                    url: RemoteConnectionURLBuilder.build(
                        pin: remoteServer.currentPin,
                        ngrokTunnelURL: remoteServer.ngrokTunnelURL,
                        lanPort: remoteServer.port
                    )
                )
            }
        }
    }

    // MARK: - Remote Devices Indicator

    @ViewBuilder
    private var remoteIndicator: some View {
        if remoteServer.connectedDeviceCount > 0 {
            Button { showingRemotePopover.toggle() } label: {
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "iphone")
                        .font(DSFont.iconSM)
                    Text("\(remoteServer.connectedDeviceCount)")
                        .font(DSFont.smallButtonLabel)
                        .monospacedDigit()
                }
                .foregroundStyle(DSColor.accentPrimary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remote Control connection")
            .accessibilityValue("\(remoteServer.connectedDeviceCount) devices")
            .help("Remote Control")
            .popover(isPresented: $showingRemotePopover, arrowEdge: .bottom) {
                RemoteDevicesPopover()
            }
        }
    }

    // MARK: - Settings Button

    private var settingsButton: some View {
        Button {
            navigationCoordinator.showingSettings = true
        } label: {
            Image(systemName: "gear")
                .foregroundStyle(DSColor.textSecondary)
                .toolbarIconButton()
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Settings")
        .help("Settings")
    }
}
