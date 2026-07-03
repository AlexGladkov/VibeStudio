// MARK: - TextFileEditorSheet
// Universal editable sheet for any plain-text file (TOML, JSON, Markdown, TypeScript, etc.).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - TextFileEditorSheet

/// Modal sheet for viewing and editing any plain-text file.
///
/// - `fileURL`: The file to open. If the file does not exist it will be created on first save.
/// - `displayTitle`: Short name shown in the toolbar (e.g. "config.toml").
/// - `defaultContent`: Pre-filled text used when `fileURL` does not exist yet.
/// - `onDismiss`: Called after a successful save so the caller can refresh its list.
struct TextFileEditorSheet: View {

    // MARK: Init

    let fileURL: URL
    var displayTitle: String
    var defaultContent: String = ""
    var onDismiss: (() -> Void)?

    // MARK: State

    @Environment(\.dismiss) private var dismiss
    @State private var vmBox = LazyStateObject<FileEditorViewModel>()

    private var vm: FileEditorViewModel {
        vmBox.resolve { FileEditorViewModel(fileURL: fileURL, defaultContent: defaultContent) }
    }

    // MARK: - Body

    var body: some View {
        let model = vm

        EditorSheetScaffold(
            title: displayTitle,
            subtitle: fileURL.tildeAbbreviatedPath,
            hasUnsavedChanges: model.hasUnsavedChanges,
            saveError: model.saveError,
            content: Binding(get: { model.content }, set: { model.content = $0 }),
            onClose: {
                onDismiss?()
                dismiss()
            },
            onSave: {
                if model.save() { onDismiss?() }
            }
        )
        .onAppear { model.load() }
    }
}
