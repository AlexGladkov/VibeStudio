// MARK: - AgentEditorSheet
// Editable sheet for creating or modifying a Claude subagent file in ~/.claude/agents/.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - AgentEditorSheet

/// Modal sheet for editing an existing subagent file or creating a new one.
///
/// - `fileURL == nil` creates a new agent in `~/.claude/agents/` after deriving
///   the filename from the `name:` frontmatter field.
/// - `fileURL != nil` overwrites the file in place on save.
/// - `onDismiss` is called both on explicit close and after a successful save
///   so the caller can refresh its agent list.
struct AgentEditorSheet: View {

    // MARK: Init

    /// File to edit, or `nil` to create a new agent.
    let fileURL: URL?
    /// Called when the sheet is closed or a save completes — use to reload the agent list.
    var onDismiss: (() -> Void)?

    // MARK: State

    @Environment(\.dismiss) private var dismiss
    @State private var vmBox = LazyStateObject<AgentEditorViewModel>()

    private var vm: AgentEditorViewModel {
        vmBox.resolve { AgentEditorViewModel(fileURL: fileURL) }
    }

    // MARK: - Body

    var body: some View {
        let model = vm

        EditorSheetScaffold(
            title: model.agentName,
            subtitle: model.agentSubtitle,
            hasUnsavedChanges: model.hasUnsavedChanges,
            saveError: model.saveError,
            content: Binding(get: { model.content }, set: { model.content = $0 }),
            onClose: {
                onDismiss?()
                dismiss()
            },
            onSave: {
                switch model.save() {
                case .saved:
                    onDismiss?()
                case .savedAndDismiss:
                    onDismiss?()
                    dismiss()
                case .none:
                    break
                }
            }
        )
        .onAppear { model.load() }
    }
}
