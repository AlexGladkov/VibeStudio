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
                assistantIcon(model.currentAssistant, size: 14)
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
                        NotificationCenter.default.post(
                            name: .showInstallAgentWizard,
                            object: assistant
                        )
                    } else {
                        model.selectAssistant(assistant)
                        showingPicker = false
                    }
                } label: {
                    HStack(spacing: 8) {
                        assistantIcon(assistant, size: 14)
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

    @ViewBuilder
    private func assistantIcon(_ assistant: AIAssistant, size: CGFloat) -> some View {
        switch assistant {
        case .claude:
            ClaudeLogoView(size: size)
        case .opencode:
            OpenCodeLogoView(size: size)
        case .codex:
            CodexLogoView(size: size)
        case .gemini:
            GeminiLogoView(size: size)
        case .qwenCode:
            QwenLogoView(size: size)
        }
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

    // MARK: - Settings Button

    private func settingsButton() -> some View {
        Button {
            NotificationCenter.default.post(name: .showAppSettings, object: nil)
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

// MARK: - OpenCode Logo View

/// Renders the OpenCode logo: two chevrons `< >` in blue-violet,
/// representing "open source" + "code editor" aesthetic.
struct OpenCodeLogoView: View {

    let size: CGFloat

    private static let logoColor = Color(red: 0.38, green: 0.55, blue: 0.95)

    var body: some View {
        Canvas { context, cs in
            let cx = cs.width / 2
            let cy = cs.height / 2
            let h  = size * 0.38   // half-height of each chevron arm
            let w  = size * 0.18   // horizontal reach of each chevron
            let gap = size * 0.09  // gap from center to tip

            // Left chevron  <
            var left = Path()
            left.move(to:    CGPoint(x: cx - gap,     y: cy))
            left.addLine(to: CGPoint(x: cx - gap - w, y: cy - h))
            left.move(to:    CGPoint(x: cx - gap,     y: cy))
            left.addLine(to: CGPoint(x: cx - gap - w, y: cy + h))

            // Right chevron  >
            var right = Path()
            right.move(to:    CGPoint(x: cx + gap,     y: cy))
            right.addLine(to: CGPoint(x: cx + gap + w, y: cy - h))
            right.move(to:    CGPoint(x: cx + gap,     y: cy))
            right.addLine(to: CGPoint(x: cx + gap + w, y: cy + h))

            let style = StrokeStyle(lineWidth: size * 0.16, lineCap: .round, lineJoin: .round)
            context.stroke(left,  with: .color(Self.logoColor), style: style)
            context.stroke(right, with: .color(Self.logoColor), style: style)
        }
        .frame(width: size, height: size)
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

// MARK: - Codex Logo View

/// Renders the OpenAI Codex logo: a green circle with white `< >` chevrons.
struct CodexLogoView: View {

    let size: CGFloat

    var body: some View {
        Canvas { context, cs in
            let cx = cs.width / 2
            let cy = cs.height / 2
            let r = size * 0.44

            // Green circle background.
            let circle = Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
            context.fill(circle, with: .color(Color(red: 0.063, green: 0.639, blue: 0.498)))

            let strokeStyle = StrokeStyle(lineWidth: size * 0.09, lineCap: .round, lineJoin: .round)

            // Left chevron <
            var left = Path()
            left.move(to: CGPoint(x: cx - r * 0.15, y: cy - r * 0.35))
            left.addLine(to: CGPoint(x: cx - r * 0.45, y: cy))
            left.addLine(to: CGPoint(x: cx - r * 0.15, y: cy + r * 0.35))
            context.stroke(left, with: .color(.white), style: strokeStyle)

            // Right chevron >
            var right = Path()
            right.move(to: CGPoint(x: cx + r * 0.15, y: cy - r * 0.35))
            right.addLine(to: CGPoint(x: cx + r * 0.45, y: cy))
            right.addLine(to: CGPoint(x: cx + r * 0.15, y: cy + r * 0.35))
            context.stroke(right, with: .color(.white), style: strokeStyle)
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Gemini Logo View

/// Renders the Google Gemini logo: a 4-pointed star (sparkle) in blue.
struct GeminiLogoView: View {

    let size: CGFloat

    var body: some View {
        Canvas { context, cs in
            let cx = cs.width / 2
            let cy = cs.height / 2
            let outer = size * 0.46
            let inner = size * 0.11

            var path = Path()
            for i in 0..<4 {
                let outerAngle = Double(i) * .pi / 2 - .pi / 2
                let innerAngle = outerAngle + .pi / 4
                let op = CGPoint(
                    x: cx + Foundation.cos(outerAngle) * outer,
                    y: cy + Foundation.sin(outerAngle) * outer
                )
                let ip = CGPoint(
                    x: cx + Foundation.cos(innerAngle) * inner,
                    y: cy + Foundation.sin(innerAngle) * inner
                )
                if i == 0 { path.move(to: op) } else { path.addLine(to: op) }
                path.addLine(to: ip)
            }
            path.closeSubpath()

            context.fill(path, with: .color(Color(red: 0.263, green: 0.522, blue: 0.957)))
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Qwen Logo View

/// Renders the Qwen Code logo: a purple "Q" shape (circle + diagonal tail).
struct QwenLogoView: View {

    let size: CGFloat

    var body: some View {
        Canvas { context, cs in
            let cx = cs.width / 2
            let cy = cs.height / 2
            let r = size * 0.32
            let lw = size * 0.13
            let color = Color(red: 0.420, green: 0.247, blue: 0.627)

            // Circle stroke.
            let circlePath = Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
            context.stroke(circlePath, with: .color(color), style: StrokeStyle(lineWidth: lw, lineCap: .round))

            // Diagonal tail of the Q.
            var tail = Path()
            tail.move(to: CGPoint(x: cx + r * 0.5, y: cy + r * 0.5))
            tail.addLine(to: CGPoint(x: cx + r * 1.1, y: cy + r * 1.1))
            context.stroke(tail, with: .color(color), style: StrokeStyle(lineWidth: lw, lineCap: .round))
        }
        .frame(width: size, height: size)
    }
}
