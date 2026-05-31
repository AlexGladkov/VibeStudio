// MARK: - CodeSpeakEditorColumn
// Centre column of the 3-column CodeSpeak layout. Hosts the syntax-highlighted
// editor for the currently selected spec or, when a generated file is
// selected, a read-only viewer with a thin header bar. Falls back to an
// empty state when nothing is selected.
//
// ARCH-C2 invariant: NEVER apply `.id(spec.id)` / `.id(generated.id)` here —
// it would force `NSScrollView` / `NSTextView` recreation on every selection
// change, destroying undo and re-spawning syntax-highlight tasks.
// `CodeSpeakEditorView.updateNSView` already syncs text via string equality.
//
// macOS 14+, Swift 5.10

import SwiftUI

/// Centre column of the CodeSpeak mode layout.
///
/// Renders the editor (or a read-only generated-file viewer) based on the
/// supplied view-model state.
struct CodeSpeakEditorColumn: View {

    let vm: CodeSpeakModeViewModel

    @Environment(\.syntaxParserRegistry) private var syntaxParserRegistry

    var body: some View {
        VStack(spacing: 0) {
            if let generated = vm.selectedGenerated {
                // Read-only: show file name in a minimal bar
                generatedFileHeader(file: generated)
                Divider()
                // ARCH-C2: NO .id(generated.id) — would force NSScrollView/NSTextView
                // recreation, destroying undo history and re-spawning syntax-highlight
                // tasks on every selection change. updateNSView already syncs text via
                // `textView.string != text` comparison.
                CodeSpeakEditorView(
                    text: .constant(vm.editorContent),
                    isEditable: false,
                    parserRegistry: syntaxParserRegistry,
                    fileExtension: generated.url.pathExtension
                )
            } else if vm.selectedSpec != nil {
                // ARCH-C2: same — no .id(spec.id).
                CodeSpeakEditorView(
                    text: Binding(
                        get: { vm.editorContent },
                        set: { vm.editorContent = $0; vm.isEditorDirty = true }
                    ),
                    isEditable: true,
                    parserRegistry: syntaxParserRegistry,
                    fileExtension: "cs.md"
                )
            } else {
                editorEmptyState
            }
        }
        .background(DSColor.surfaceBase)
    }

    // MARK: - Generated File Header

    private func generatedFileHeader(file: GeneratedFile) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Text(file.name)
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            Text("read-only")
                .font(.system(size: 9, weight: .medium)) // intentional sub-grid badge
                .foregroundStyle(DSColor.textMuted)
                .padding(.horizontal, DSSpacing.xs)
                .padding(.vertical, DSSpacing.xxs)
                .background(
                    DSColor.mutedBadgeBg,
                    in: RoundedRectangle(cornerRadius: DSRadius.sm)
                )

            Spacer()
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    // MARK: - Empty State

    private var editorEmptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "doc.text")
                .font(DSFont.emptyStateIconLarge)
                .foregroundStyle(DSColor.textMuted)
            Text("Select a spec to edit")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DSColor.surfaceBase)
    }
}
