// MARK: - LLMSettingsPane
// Placeholder pane for per-assistant LLM settings.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - LLMSettingsPane

/// Placeholder pane displayed when an LLM assistant is selected in settings.
///
/// Shows an "under construction" state until per-assistant configuration
/// is implemented in a future release.
struct LLMSettingsPane: View {

    let assistant: AIAssistant

    var body: some View {
        UnderConstructionPane(title: assistant.displayName)
    }
}
