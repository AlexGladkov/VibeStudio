// MARK: - LLMSettingsPane
// Dispatches to the appropriate settings pane for each AI assistant.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - LLMSettingsPane

/// Routes to the correct settings pane based on the selected ``AIAssistant``.
///
/// Each assistant has a dedicated settings pane:
/// Claude → ``ClaudeSettingsPane``, Opencode → ``OpencodeSettingsPane``,
/// Codex → ``CodexSettingsPane``, Gemini → ``GeminiSettingsPane``,
/// Qwen → ``QwenSettingsPane``.
struct LLMSettingsPane: View {

    let assistant: AIAssistant

    var body: some View {
        switch assistant {
        case .claude:
            ClaudeSettingsPane()
        case .opencode:
            OpencodeSettingsPane()
        case .codex:
            CodexSettingsPane()
        case .gemini:
            GeminiSettingsPane()
        case .qwenCode:
            QwenSettingsPane()
        case .codeSpeak:
            CodeSpeakSettingsPane()
        }
    }
}
