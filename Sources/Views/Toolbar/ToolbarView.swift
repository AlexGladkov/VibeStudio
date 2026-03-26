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
    @Environment(\.openURL) private var openURL

    @State private var vm: ToolbarViewModel?
    @State private var showingPicker = false

    private var viewModel: ToolbarViewModel {
        if let existing = vm { return existing }
        let created = ToolbarViewModel(projectManager: projectManager, terminalManager: terminalManager)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel
        HStack(spacing: 6) {
            configPicker(model: model)
            playStopButton(model: model)
            openInBrowserButton(model: model)
        }
        .padding(.horizontal, 12)
        .onAppear {
            if vm == nil {
                vm = ToolbarViewModel(projectManager: projectManager, terminalManager: terminalManager)
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
                Button {
                    model.selectAssistant(assistant)
                    showingPicker = false
                } label: {
                    HStack(spacing: 8) {
                        assistantIcon(assistant, size: 14)

                        Text(assistant.displayName)
                            .font(.system(size: 13))
                            .foregroundStyle(DSColor.textPrimary)

                        Spacer()

                        if assistant == model.currentAssistant {
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

    @ViewBuilder
    private func assistantIcon(_ assistant: AIAssistant, size: CGFloat) -> some View {
        switch assistant {
        case .claude:
            ClaudeLogoView(size: size)
        case .opencode:
            OpenCodeLogoView(size: size)
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
