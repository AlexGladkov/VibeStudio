// MARK: - Agent Logo Views
// Canonical logo renderers for all supported AI assistants.
// Used by AIAssistantIconView — do not render directly from feature code.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - OpenCode Logo View

/// Renders the OpenCode logo: two chevrons `< >` in blue-violet,
/// representing "open source" + "code editor" aesthetic.
struct OpenCodeLogoView: View {

    let size: CGFloat

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
            context.stroke(left,  with: .color(DSColor.agentOpenCode), style: style)
            context.stroke(right, with: .color(DSColor.agentOpenCode), style: style)
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
                with: .color(DSColor.agentClaude),
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
            context.fill(circle, with: .color(DSColor.agentCodex))

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

            context.fill(path, with: .color(DSColor.agentGemini))
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

            // Circle stroke.
            let circlePath = Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
            context.stroke(circlePath, with: .color(DSColor.agentQwen), style: StrokeStyle(lineWidth: lw, lineCap: .round))

            // Diagonal tail of the Q.
            var tail = Path()
            tail.move(to: CGPoint(x: cx + r * 0.5, y: cy + r * 0.5))
            tail.addLine(to: CGPoint(x: cx + r * 1.1, y: cy + r * 1.1))
            context.stroke(tail, with: .color(DSColor.agentQwen), style: StrokeStyle(lineWidth: lw, lineCap: .round))
        }
        .frame(width: size, height: size)
    }
}
