// MARK: - ToolbarView
// Android Studio-style run-configuration bar above the tab bar.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI

// MARK: - ToolbarView

/// Android Studio-style run-configuration toolbar above the tab bar.
///
/// Layout switches based on `AppMode`:
/// - Regular:   `[ claude v ]  [ > / # ]  [ globe ]  [ gear ]  [ CS ]  [ sidebar.right ]`
/// - CodeSpeak: `[ magnifier ProjectName v ]  [ > Build ]  [ gear ]  [ <- Regular ]`
struct ToolbarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.agentAvailability) private var agentAvailability
    // Observable-style injection — required for currentMode reactive tracking.
    @Environment(AppNavigationCoordinator.self) private var navigationCoordinator
    @Environment(\.codeSpeak) private var codeSpeak
    @Environment(\.freeTabStore) private var freeTabStore
    @Environment(\.openURL) private var openURL

    @State private var vm: ToolbarViewModel?
    @State private var showingPicker = false
    @State private var showingProjectPicker = false

    private var viewModel: ToolbarViewModel {
        if let existing = vm { return existing }
        let created = ToolbarViewModel(
            projectManager: projectManager,
            terminalManager: terminalManager,
            agentAvailability: agentAvailability
        )
        Task { @MainActor in vm = created }
        return created
    }

    private var activeProject: Project? { projectManager.activeProject }

    var body: some View {
        let model = viewModel
        let isWelcomeScreen = (projectManager.projects.isEmpty || projectManager.activeProjectId == nil)
            && freeTabStore.freeTabs.isEmpty
        Group {
            if !isWelcomeScreen {
                if navigationCoordinator.currentMode == .codeSpeak {
                    codeSpeakThreeSectionToolbar()
                } else {
                    HStack(spacing: DSSpacing.sm) {
                        configPicker(model: model)
                        playStopButton(model: model)
                        openInBrowserButton(model: model)
                        settingsButton()
                        changesToggleButton()
                    }
                    .padding(.horizontal, DSSpacing.md)
                }
            }
        }
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

    // MARK: - CodeSpeak Three-Section Toolbar
    //
    // The ToolbarView NSHostingView starts at trafficLightsEnd ≈ 84pt from the
    // window's left edge. The content area has three columns:
    //   Left   (specs sidebar): starts at x=0, width = specsColumnWidth (dynamic)
    //   Center (editor):        starts at x=specsColumnWidth
    //   Right  (build output):  trailing portion
    //
    // Three boxes mirror this layout inside the hosting view:
    //   Box 1 — invisible spacer: width = specsColumnWidth - 84 (the part of the
    //           sidebar that is RIGHT OF the traffic lights)
    //   Box 2 — breadcrumb + stats badge, left-aligned in center column
    //   Box 3 — run bar + settings, right-aligned
    //
    // specsColumnWidth is dynamically updated by CodeSpeakModeView via GeometryReader
    // so the breadcrumb tracks sidebar resize automatically.

    private func codeSpeakThreeSectionToolbar() -> some View {
        // trafficLightsEnd — the standard macOS value used in WindowToolbarRemover.
        // The hosting view's leading anchor is set to this value, so subtracting it gives
        // the offset within the hosting view where the specs column ends.
        let trafficLightsEnd = DSLayout.trafficLightsEndFallback
        let specsWidth = navigationCoordinator.specsColumnWidth
        let box1Width = max(0, specsWidth - trafficLightsEnd)

        return HStack(spacing: 0) {
            // Box 1 — left panel placeholder (transparent)
            Color.clear
                .frame(width: box1Width)

            // Box 2 — center: breadcrumb pinned to left of center column
            HStack(spacing: DSSpacing.sm) {
                codeSpeakBreadcrumb()
                    .padding(.leading, DSSpacing.sm)
                codeSpeakStatsBadge()
            }

            Spacer(minLength: DSSpacing.sm)

            // Box 3 — right: run controls
            HStack(spacing: DSSpacing.sm) {
                codeSpeakRunBar()
                settingsButton()
            }
            .padding(.trailing, DSSpacing.md)
        }
    }

    // MARK: - Configuration Picker

    private func configPicker(model: ToolbarViewModel) -> some View {
        Button {
            guard !model.isRunning, model.activeId != nil else { return }
            showingPicker.toggle()
        } label: {
            HStack(spacing: DSSpacing.xs) {
                AIAssistantIconView(assistant: model.currentAssistant, size: 14)
                    .opacity(model.isRunning ? 0.4 : 1.0)

                Text(model.currentAssistant.displayName)
                    .font(DSFont.tabTitle)
                    .foregroundStyle(model.isRunning ? DSColor.textMuted : DSColor.textPrimary)

                Image(systemName: "chevron.down")
                    .font(DSFont.iconSM)
                    .foregroundStyle(model.isRunning ? DSColor.textMuted : DSColor.textSecondary)
            }
            .frame(height: DSLayout.toolbarButtonHeight)
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
                    HStack(spacing: DSSpacing.sm) {
                        AIAssistantIconView(assistant: assistant, size: 14)
                            .opacity(canLaunch ? 1.0 : 0.5)

                        VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                            Text(assistant.displayName)
                                .font(DSFont.sidebarItem)
                                .foregroundStyle(canLaunch ? DSColor.textPrimary : DSColor.textSecondary)

                            if isNotInstalled {
                                Text("Нажми для установки")
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

                        if isNotInstalled {
                            Image(systemName: "arrow.down.circle")
                                .font(DSFont.iconLG)
                                .foregroundStyle(DSColor.accentPrimary)
                        } else if !canLaunch {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(DSFont.iconMD)
                                .foregroundStyle(DSColor.indicatorWaiting)
                        } else if assistant == model.currentAssistant {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(DSColor.accentPrimary)
                        }
                    }
                    .padding(.horizontal, DSSpacing.md)
                    .padding(.vertical, DSSpacing.sm)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(minWidth: DSLayout.popoverMinWidth)
        .padding(.vertical, DSSpacing.xs)
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
            .frame(width: DSLayout.toolbarIconButtonWidth, height: DSLayout.toolbarButtonHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.activeId == nil)
    }

    // MARK: - CodeSpeak Project Picker (Breadcrumb)

    /// Breadcrumb: [Projects  ›  🔍 ProjectName ▾]
    /// Tapping "Projects" deactivates the current project → start screen.
    /// Tapping the project name opens the CS-project switcher popover.
    private func codeSpeakProjectPicker() -> some View {
        HStack(spacing: 0) {
            // "Projects" root — taps go to start screen
            Button {
                projectManager.activeProjectId = nil
            } label: {
                Text("Projects")
                    .font(DSFont.tabTitle)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("All Projects")

            Image(systemName: "chevron.right")
                .font(DSFont.iconSM)
                .foregroundStyle(DSColor.textGhost)
                .padding(.horizontal, DSSpacing.xs)

            // Project name + dropdown
            Button {
                showingProjectPicker.toggle()
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "doc.text.magnifyingglass")
                        .font(DSFont.tabTitle)
                        .foregroundStyle(DSColor.agentCodeSpeak)

                    Text(activeProject?.name ?? "CodeSpeak")
                        .font(DSFont.tabTitle)
                        .foregroundStyle(DSColor.textPrimary)
                        .lineLimit(1)

                    Image(systemName: "chevron.down")
                        .font(DSFont.iconSM)
                        .foregroundStyle(DSColor.textSecondary)
                }
            }
            .buttonStyle(.plain)
            .popover(isPresented: $showingProjectPicker, arrowEdge: .bottom) {
                projectPickerPopover
            }
        }
        .padding(.horizontal, DSSpacing.sm)
        .padding(.vertical, DSSpacing.xxs)
        .background(DSColor.agentCodeSpeak.opacity(0.08), in: RoundedRectangle(cornerRadius: DSRadius.md))
    }

    private var projectPickerPopover: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(projectManager.projects.filter { codeSpeak.isCodeSpeakProject($0.id) }) { project in
                Button {
                    projectManager.activeProjectId = project.id
                    showingProjectPicker = false
                } label: {
                    HStack(spacing: DSSpacing.sm) {
                        if project.id == projectManager.activeProjectId {
                            Image(systemName: "checkmark")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(DSColor.accentPrimary)
                        } else {
                            Color.clear.frame(width: 11)
                        }

                        Text(project.name)
                            .font(DSFont.sidebarItem)
                            .foregroundStyle(DSColor.textPrimary)
                            .lineLimit(1)

                        Spacer()
                    }
                    .padding(.horizontal, DSSpacing.md)
                    .padding(.vertical, DSSpacing.sm)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }

        }
        .frame(minWidth: DSLayout.popoverMinWidth)
        .padding(.vertical, DSSpacing.xs)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - CodeSpeak Stats Badge

    @ViewBuilder
    private func codeSpeakStatsBadge() -> some View {
        if let id = projectManager.activeProjectId,
           let stats = codeSpeak.projectStats[id] {
            Text("\(stats.passing)/\(stats.total)")
                .font(DSFont.smallButtonLabel)
                .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
                .padding(.horizontal, DSSpacing.xs)
                .padding(.vertical, DSSpacing.xxs)
                .background(
                    stats.allPassing ? DSColor.diffAddedBg : DSColor.diffDeletedBg,
                    in: RoundedRectangle(cornerRadius: DSRadius.sm)
                )
        }
    }

    // MARK: - CodeSpeak Breadcrumb

    /// Static breadcrumb on the leading side of the titlebar:
    /// `Projects  ›  projectName  ›  spec.cs.md`
    ///
    /// "Projects" is clickable (returns to welcome screen).
    /// Project and spec names are plain text — no dropdown, one project at a time.
    private func codeSpeakBreadcrumb() -> some View {
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
                }
            }
        }
    }

    // MARK: - CodeSpeak Run Bar

    /// Returns `true` if the current command can be launched (non-empty required inputs).
    private var codeSpeakCanRun: Bool {
        switch navigationCoordinator.runBar.command {
        case .task:   return !navigationCoordinator.runBar.taskName.trimmingCharacters(in: .whitespaces).isEmpty
        case .change: return !navigationCoordinator.runBar.changeMessage.trimmingCharacters(in: .whitespaces).isEmpty
        default:      return true
        }
    }

    /// Toolbar run bar: `[Command ▼] [optional text field] [▶/■]`
    ///
    /// Replaces the old hardcoded `▶ Build` button.
    /// Command state lives in `AppNavigationCoordinator` so the toolbar
    /// and `CodeSpeakModeView` share the same source of truth.
    private func codeSpeakRunBar() -> some View {
        HStack(spacing: DSSpacing.sm) {
            // CodeSpeak icon (moved here from the right panel header)
            Image(systemName: "doc.text.magnifyingglass")
                .font(DSFont.tabTitle)
                .foregroundStyle(DSColor.agentCodeSpeak)

            // Command dropdown
            Menu {
                ForEach(CodeSpeakCommand.allCases) { cmd in
                    Button(cmd.displayName) {
                        navigationCoordinator.runBar.command = cmd
                    }
                }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Text(navigationCoordinator.runBar.command.displayName)
                        .font(DSFont.tabTitle)
                        .foregroundStyle(
                            navigationCoordinator.runBar.isRunning
                                ? DSColor.textMuted
                                : DSColor.textPrimary
                        )
                    Image(systemName: "chevron.down")
                        .font(DSFont.iconSM)
                        .foregroundStyle(DSColor.textSecondary)
                }
                .frame(height: DSLayout.toolbarButtonHeight)
            }
            .menuStyle(.borderlessButton)
            .fixedSize()
            .disabled(navigationCoordinator.runBar.isRunning)

            // Inline text field for commands that require input
            if navigationCoordinator.runBar.command == .task {
                TextField(
                    "Task name...",
                    text: Binding(
                        get: { navigationCoordinator.runBar.taskName },
                        set: { navigationCoordinator.runBar.taskName = $0 }
                    )
                )
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
            } else if navigationCoordinator.runBar.command == .change {
                TextField(
                    "Describe the change...",
                    text: Binding(
                        get: { navigationCoordinator.runBar.changeMessage },
                        set: { navigationCoordinator.runBar.changeMessage = $0 }
                    )
                )
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

            // Play / Stop button (triangle on the RIGHT)
            Button {
                if navigationCoordinator.runBar.isRunning {
                    navigationCoordinator.runBar.stopRequested = true
                } else {
                    navigationCoordinator.codeSpeakBuildRequested = true
                }
            } label: {
                Image(
                    systemName: navigationCoordinator.runBar.isRunning
                        ? "stop.fill"
                        : "play.fill"
                )
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(
                    navigationCoordinator.runBar.isRunning
                        ? DSColor.actionStop
                        : (codeSpeakCanRun ? DSColor.actionRun : DSColor.textMuted)
                )
                .frame(width: DSLayout.toolbarButtonHeight, height: DSLayout.toolbarButtonHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!navigationCoordinator.runBar.isRunning && !codeSpeakCanRun)
            .help(
                navigationCoordinator.runBar.isRunning
                    ? "Stop codespeak"
                    : "Run codespeak \(navigationCoordinator.runBar.command.displayName.lowercased())"
            )
        }
    }

    // MARK: - Changes Panel Toggle

    private func changesToggleButton() -> some View {
        Button {
            withAnimation(.easeOut(duration: 0.2)) {
                navigationCoordinator.showingChangesPanel.toggle()
            }
        } label: {
            Image(systemName: "sidebar.right")
                .foregroundStyle(
                    navigationCoordinator.showingChangesPanel
                        ? DSColor.accentPrimary
                        : DSColor.textSecondary
                )
                .toolbarIconButton()
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
                .foregroundStyle(DSColor.textSecondary)
                .toolbarIconButton()
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
                .foregroundStyle(model.activeProductionURL != nil ? DSColor.textPrimary : DSColor.textMuted)
                .toolbarIconButton()
        }
        .buttonStyle(.plain)
        .disabled(model.activeProductionURL == nil)
        .help(model.activeProductionURL.map { "Open \($0.absoluteString) in browser" } ?? "No production URL set")
    }
}
