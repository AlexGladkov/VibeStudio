// MARK: - ClaudeEditorSheet
// Editable sheet for ~/.claude/CLAUDE.md.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - ClaudeEditorSheet

/// Modal sheet providing a full-height editable NSTextView for CLAUDE.md.
struct ClaudeEditorSheet: View {

    @Environment(\.dismiss) private var dismiss
    @State private var vmBox = LazyStateObject<FileEditorViewModel>()

    private static let fileURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/CLAUDE.md")

    private var vm: FileEditorViewModel {
        vmBox.resolve { FileEditorViewModel(fileURL: Self.fileURL) }
    }

    // MARK: - Body

    var body: some View {
        let model = vm

        EditorSheetScaffold(
            title: "CLAUDE.md",
            subtitle: "~/.claude/CLAUDE.md",
            hasUnsavedChanges: model.hasUnsavedChanges,
            saveError: model.saveError,
            content: Binding(get: { model.content }, set: { model.content = $0 }),
            onClose: { dismiss() },
            onSave: { model.save() }
        )
        .onAppear { model.load() }
    }
}
