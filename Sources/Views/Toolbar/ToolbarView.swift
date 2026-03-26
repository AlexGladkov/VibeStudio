// MARK: - ToolbarView
// Android Studio–style run-configuration bar above the tab bar.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI

// MARK: - AI Assistant

/// Supported AI code assistants.
/// Currently only Claude Code; extend when adding others.
enum AIAssistant: String, CaseIterable, Identifiable {
    case claude

    var id: String { rawValue }
    var displayName: String { rawValue }

    /// Shell command to start this assistant.
    var launchCommand: String {
        switch self {
        case .claude: return "claude --dangerously-skip-permissions\n"
        }
    }
}

// MARK: - ToolbarView

/// Android Studio–style run-configuration toolbar above the tab bar.
///
/// Layout: `[ 🤖 claude ▾ ]  [ ▶ / ■ ]  ···`
///
/// Per-project state:
/// - selected assistant (defaults to `.claude`)
/// - running flag (disables picker, switches ▶ → ■)
struct ToolbarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.openURL) private var openURL

    @State private var runningAssistants: [UUID: Bool] = [:]
    @State private var selectedAssistants: [UUID: AIAssistant] = [:]
    @State private var showingPicker = false

    // MARK: - Helpers

    private var activeId: UUID? { projectManager.activeProjectId }

    private var isRunning: Bool {
        guard let id = activeId else { return false }
        return runningAssistants[id] == true
    }

    private var currentAssistant: AIAssistant {
        guard let id = activeId else { return .claude }
        return selectedAssistants[id] ?? .claude
    }

    private var activeProductionURL: URL? {
        guard let id = activeId,
              let project = projectManager.projects.first(where: { $0.id == id }),
              let urlString = project.productionURL,
              !urlString.isEmpty,
              let url = URL(string: urlString) else { return nil }
        return url
    }

    // MARK: - Body

    var body: some View {
        HStack(spacing: 6) {
            configPicker
            playStopButton
            openInBrowserButton
        }
        .padding(.trailing, 16)
    }

    // MARK: - Configuration Picker

    /// `[ 🤖 claude ▾ ]` button — opens assistant selection popover.
    /// Uses Button+Popover (not Menu) for full rendering control on macOS.
    private var configPicker: some View {
        Button {
            guard !isRunning, activeId != nil else { return }
            showingPicker.toggle()
        } label: {
            HStack(spacing: 5) {
                ClaudeLogoView(size: 14)
                    .opacity(isRunning ? 0.4 : 1.0)

                Text(currentAssistant.displayName)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(isRunning ? DSColor.textMuted : DSColor.textPrimary)

                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(isRunning ? DSColor.textMuted : DSColor.textSecondary)
            }
            .frame(height: 22)
        }
        .buttonStyle(.plain)
        .disabled(isRunning || activeId == nil)
        .popover(isPresented: $showingPicker, arrowEdge: .bottom) {
            pickerPopover
        }
    }

    // MARK: - Picker Popover Content

    private var pickerPopover: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(AIAssistant.allCases) { assistant in
                Button {
                    if let id = activeId {
                        selectedAssistants[id] = assistant
                    }
                    showingPicker = false
                } label: {
                    HStack(spacing: 8) {
                        ClaudeLogoView(size: 14)

                        Text(assistant.displayName)
                            .font(.system(size: 13))
                            .foregroundStyle(DSColor.textPrimary)

                        Spacer()

                        if assistant == currentAssistant {
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
        .frame(minWidth: 160)
        .padding(.vertical, 4)
        .background(DSColor.surfaceOverlay)
    }

    // MARK: - Play / Stop Button

    /// Icon-only ▶ / ■ button, no background (matching AS style).
    private var playStopButton: some View {
        Button {
            if isRunning { stopAssistant() } else { startAssistant() }
        } label: {
            Group {
                if isRunning {
                    Image(systemName: "stop.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(DSColor.actionStop)
                } else {
                    Image(systemName: "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(
                            activeId == nil ? DSColor.textMuted : DSColor.actionRun
                        )
                }
            }
            .frame(width: 26, height: 22)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(activeId == nil)
    }

    // MARK: - Open in Browser Button

    /// Globe icon — opens the active project's production URL in the default browser.
    /// Disabled (dimmed) when no production URL is set for the active project.
    private var openInBrowserButton: some View {
        Button {
            if let url = activeProductionURL {
                openURL(url)
            }
        } label: {
            Image(systemName: "globe")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(activeProductionURL != nil ? DSColor.textPrimary : DSColor.textMuted)
                .frame(width: 26, height: 22)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(activeProductionURL == nil)
        .help(activeProductionURL.map { "Open \($0.absoluteString) in browser" } ?? "No production URL set")
    }

    // MARK: - Actions

    private func startAssistant() {
        let id = activeId
        let sessions = id.map { terminalManager.sessions(for: $0) } ?? []
        Logger.ui.debug("startAssistant: activeId=\(String(describing: id), privacy: .public), sessions=\(sessions.count)")
        guard let id, let session = sessions.first else {
            Logger.ui.debug("startAssistant: guard failed — no active project or no terminal sessions")
            return
        }
        Logger.ui.debug("startAssistant: launching \(self.currentAssistant.displayName, privacy: .public) in session \(session.id)")
        runningAssistants[id] = true
        terminalManager.sendInput(currentAssistant.launchCommand, to: session.id)
    }

    private func stopAssistant() {
        guard let id = activeId,
              let session = terminalManager.sessions(for: id).first else { return }
        terminalManager.sendInput("\u{03}", to: session.id)
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(300))
            terminalManager.sendInput("/exit\n", to: session.id)
            runningAssistants[id] = false
        }
    }
}

// MARK: - Claude Logo View

/// Renders the Claude AI logo: 8 spokes radiating outward from a small
/// center gap, colored in the official copper tone.
///
/// Uses Canvas stroke with round caps — avoids center-blob artifacts
/// that occur when capsules fully overlap at small icon sizes.
struct ClaudeLogoView: View {

    let size: CGFloat

    private static let logoColor = Color(red: 0.80, green: 0.47, blue: 0.28)

    var body: some View {
        Canvas { context, cs in
            let cx  = cs.width  / 2
            let cy  = cs.height / 2
            // Spokes extend from innerR → outerR (gap at center prevents blob).
            let outerR = size * 0.46
            let innerR = size * 0.14

            var path = Path()
            for i in 0..<8 {
                let a   = Double(i) * .pi / 4
                let cos = Foundation.cos(a)
                let sin = Foundation.sin(a)
                path.move(
                    to: CGPoint(x: cx + cos * innerR, y: cy + sin * innerR)
                )
                path.addLine(
                    to: CGPoint(x: cx + cos * outerR, y: cy + sin * outerR)
                )
            }

            context.stroke(
                path,
                with: .color(Self.logoColor),
                style: StrokeStyle(lineWidth: size * 0.16, lineCap: .round)
            )
        }
        .frame(width: size, height: size)
    }
}
