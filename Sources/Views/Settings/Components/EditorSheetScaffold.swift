// MARK: - EditorSheetScaffold
// Reusable container for the Settings editor sheets (text / Claude / command / agent / skill).
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - EditorSheetScaffold

/// Shared visual scaffold for every editor sheet in Settings.
///
/// All editor sheets share the same skeleton — a raised toolbar (title + mono
/// subtitle + optional accessory + close button), a `MarkdownEditorView`, and a
/// raised bottom bar (status text + optional accessory + save button). This
/// container owns that layout so each sheet becomes a thin configurator that
/// wires up its view model.
///
/// - Read-only mode (`readOnly == true`) hides the save button and shows
///   `readOnlyMessage` in place of the unsaved-changes indicator.
/// - `toolbarAccessory` is injected between the subtitle and the trailing close
///   button (used for the command filename field and the skill lock badge).
/// - `bottomAccessory` is injected between the status spacer and the save button
///   (used for the command sheet's extra "Закрыть" button).
struct EditorSheetScaffold<ToolbarAccessory: View, BottomAccessory: View>: View {

    // MARK: Configuration

    let title: String
    var subtitle: String?
    var readOnly: Bool
    var readOnlyMessage: String?
    let hasUnsavedChanges: Bool
    let saveError: String?
    var minEditorHeight: CGFloat?

    @Binding var content: String

    let onClose: () -> Void
    let onSave: () -> Void

    private let toolbarAccessory: ToolbarAccessory
    private let bottomAccessory: BottomAccessory

    // MARK: Init

    init(
        title: String,
        subtitle: String? = nil,
        readOnly: Bool = false,
        readOnlyMessage: String? = nil,
        hasUnsavedChanges: Bool,
        saveError: String?,
        minEditorHeight: CGFloat? = nil,
        content: Binding<String>,
        onClose: @escaping () -> Void,
        onSave: @escaping () -> Void,
        @ViewBuilder toolbarAccessory: () -> ToolbarAccessory = { EmptyView() },
        @ViewBuilder bottomAccessory: () -> BottomAccessory = { EmptyView() }
    ) {
        self.title = title
        self.subtitle = subtitle
        self.readOnly = readOnly
        self.readOnlyMessage = readOnlyMessage
        self.hasUnsavedChanges = hasUnsavedChanges
        self.saveError = saveError
        self.minEditorHeight = minEditorHeight
        self._content = content
        self.onClose = onClose
        self.onSave = onSave
        self.toolbarAccessory = toolbarAccessory()
        self.bottomAccessory = bottomAccessory()
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().background(DSColor.borderDefault)
            MarkdownEditorView(text: $content, isEditable: !readOnly)
                .frame(maxWidth: .infinity, minHeight: minEditorHeight, maxHeight: .infinity)
            Divider().background(DSColor.borderDefault)
            bottomBar
        }
        .frame(width: DSLayout.editorSheetWidth, height: DSLayout.editorSheetHeight)
        .background(DSColor.surfaceDefault)
    }

    // MARK: - Toolbar

    private var toolbar: some View {
        HStack(spacing: DSSpacing.sm) {
            Text(title)
                .font(DSFont.sheetTitle)
                .foregroundStyle(DSColor.textPrimary)

            if let subtitle {
                Text(subtitle)
                    .font(DSFont.monoSmall)
                    .foregroundStyle(DSColor.textMuted)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }

            toolbarAccessory

            Spacer()

            SheetCloseButton(action: onClose)
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack(spacing: DSSpacing.sm) {
            statusText

            Spacer()

            bottomAccessory

            if !readOnly {
                Button("Сохранить", action: onSave)
                    .keyboardShortcut("s", modifiers: .command)
                    .disabled(!hasUnsavedChanges)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    @ViewBuilder
    private var statusText: some View {
        if readOnly, let readOnlyMessage {
            Text(readOnlyMessage)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        } else if let saveError {
            Text(saveError)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.gitDeleted)
        } else if hasUnsavedChanges {
            Text("Есть несохранённые изменения")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
        }
    }
}
