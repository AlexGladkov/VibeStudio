// MARK: - AIAssistantIconView
// Centralizes the AI assistant icon rendering that was duplicated
// in ToolbarView and SettingsView.
// macOS 14+, Swift 5.10

import SwiftUI

/// Displays the icon for an AI assistant, with configurable size.
///
/// Centralizes the icon rendering that was duplicated in ToolbarView and SettingsView.
/// Uses the canonical logo views from ``AgentLogos``.
///
/// - Parameters:
///   - assistant: The AI assistant whose icon to render.
///   - size: The width and height of the icon in points (default `16`).
struct AIAssistantIconView: View {

    let assistant: AIAssistant
    var size: CGFloat = 16

    var body: some View {
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
}
