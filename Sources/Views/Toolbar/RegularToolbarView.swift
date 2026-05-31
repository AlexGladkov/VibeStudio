// MARK: - RegularToolbarView
// Regular-mode toolbar: AI config picker, run/stop, open-in-browser, remote, settings.
// macOS 14+, Swift 5.10

import SwiftUI

/// Trailing-aligned toolbar shown in Regular mode (non-CodeSpeak projects).
///
/// Layout: `[ assistant ▾ ]  [ ▶/■ ]  [ globe ]  [ qr ]  [ devices ]  [ gear ]  [ sidebar.right ]`
///
/// State lives in `ToolbarViewModel` (selected assistant, isRunning, project URL)
/// and `AppNavigationCoordinator` (settings sheet, changes panel visibility).
///
/// Extracted from the monolithic `ToolbarView` so each toolbar section is a
/// self-contained file (≤ 200 LOC) — see ARCH-H6 in the refactoring triage.
struct RegularToolbarView: View {

    @Environment(\.openURL) private var openURL
    @Environment(\.remoteControlServer) private var remoteServer
    @Environment(AppNavigationCoordinator.self) private var navigationCoordinator

    let model: ToolbarViewModel

    @State private var showingPicker = false
    @State private var showingRemotePopover = false
    @State private var showingQRPopover = false

    var body: some View {
        HStack(spacing: DSSpacing.sm) {
            Spacer(minLength: 0)

            configPicker
            playStopButton
            openInBrowserButton

            remoteQRButton
            remoteIndicator
            settingsButton
            changesToggleButton
        }
        .padding(.horizontal, DSSpacing.md)
    }

    // MARK: - Configuration Picker

    private var configPicker: some View {
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
            ToolbarConfigPicker(model: model, showingPicker: $showingPicker)
        }
    }

    // MARK: - Play / Stop Button

    private var playStopButton: some View {
        // Regular mode: `canRun` is just "an assistant is selected" — the
        // button is fully disabled when `activeId == nil` (matches the
        // previous `.disabled(model.activeId == nil)` invariant).
        PlayStopButton(
            isRunning: model.isRunning,
            canRun: model.activeId != nil,
            size: .regular
        ) {
            if model.isRunning {
                model.stopAssistant()
            } else {
                model.startAssistant()
            }
        }
    }

    // MARK: - Open in Browser Button

    private var openInBrowserButton: some View {
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
            .help("QR-код для подключения")
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
        .help("Settings")
    }

    // MARK: - Changes Panel Toggle

    private var changesToggleButton: some View {
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
}
