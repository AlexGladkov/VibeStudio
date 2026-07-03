// MARK: - CommandEditorSheet
// Sheet for creating or editing a Claude command markdown file in ~/.claude/commands/.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - CommandEditorSheet

/// Modal sheet for editing an existing command file or creating a new one.
///
/// - `fileURL == nil` creates a new `.md` file in `~/.claude/commands/`.
/// - `fileURL != nil` overwrites the file in place on save.
/// - `onSaved` is called after a successful save so the caller can refresh its command list.
struct CommandEditorSheet: View {

    // MARK: Init

    /// File to edit, or `nil` to create a new command.
    let fileURL: URL?
    /// Called after a successful save.
    var onSaved: (() -> Void)?

    // MARK: State

    @Environment(\.dismiss) private var dismiss
    @State private var vmBox = LazyStateObject<CommandEditorViewModel>()

    private var vm: CommandEditorViewModel {
        vmBox.resolve { CommandEditorViewModel(fileURL: fileURL) }
    }

    private var title: String {
        if fileURL == nil { return "Новая команда" }
        return fileURL?.deletingPathExtension().lastPathComponent ?? "Команда"
    }

    // MARK: - Body

    var body: some View {
        let model = vm

        EditorSheetScaffold(
            title: title,
            subtitle: fileURL?.tildeAbbreviatedPath,
            hasUnsavedChanges: model.hasUnsavedChanges,
            saveError: model.saveError,
            minEditorHeight: 300,
            content: Binding(get: { model.content }, set: { model.content = $0 }),
            onClose: { dismiss() },
            onSave: {
                switch model.save() {
                case .saved:
                    onSaved?()
                case .savedAndDismiss:
                    onSaved?()
                    dismiss()
                case .none:
                    break
                }
            },
            toolbarAccessory: {
                if model.isNewFile {
                    HStack(spacing: DSSpacing.xs) {
                        TextField("имя-файла", text: Binding(get: { model.filename }, set: { model.filename = $0 }))
                            .font(DSFont.monoSmall)
                            .textFieldStyle(.roundedBorder)
                            .frame(width: DSLayout.settingsLabelWidthLong)

                        Text(".md")
                            .font(DSFont.monoSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }
                }
            },
            bottomAccessory: {
                Button("Закрыть") {
                    dismiss()
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
        )
        .onAppear { model.load() }
    }
}
