// MARK: - SpecEditorSheet
// Split-view markdown editor for a single .cs.md spec file.
// macOS 14+, Swift 5.10

import SwiftUI
import AppKit

/// Modal sheet for editing a `.cs.md` spec file.
///
/// Left pane: Syntax-highlighted editor (``CodeSpeakEditorView``).
/// Right pane: rendered Text preview.
/// Sheet size: 900 x 600.
struct SpecEditorSheet: View {

    let specFile: SpecFile

    @Environment(\.dismiss) private var dismiss
    @Environment(\.syntaxParserRegistry) private var syntaxParserRegistry

    @State private var vm: SpecEditorViewModel?

    private var viewModel: SpecEditorViewModel {
        if let existing = vm { return existing }
        let created = SpecEditorViewModel(specFile: specFile)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        let model = viewModel

        VStack(spacing: 0) {
            // Header
            HStack {
                Image(systemName: "doc.text")
                    .foregroundStyle(DSColor.agentCodeSpeak)
                Text(specFile.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(DSColor.textPrimary)
                if model.isDirty {
                    Text("•")
                        .foregroundStyle(DSColor.gitModified)
                }
                Spacer()
            }
            .padding(.horizontal, DSSpacing.lg)
            .padding(.vertical, DSSpacing.md)

            Divider()

            // Split editor / preview
            HSplitView {
                // Editor pane
                VStack(spacing: 0) {
                    Text("EDIT")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, DSSpacing.md)
                        .padding(.vertical, DSSpacing.xs)
                        .background(DSColor.surfaceOverlay)
                    Divider()
                    CodeSpeakEditorView(
                        text: Binding(
                            get: { model.content },
                            set: { newVal in
                                model.content = newVal
                                model.markDirty()
                            }
                        ),
                        isEditable: true,
                        parserRegistry: syntaxParserRegistry,
                        fileExtension: "cs.md"
                    )
                    .background(DSColor.surfaceBase)
                }

                // Preview pane
                VStack(spacing: 0) {
                    Text("PREVIEW")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, DSSpacing.md)
                        .padding(.vertical, DSSpacing.xs)
                        .background(DSColor.surfaceOverlay)
                    Divider()
                    ScrollView {
                        Text(model.content)
                            .font(.system(size: 13))
                            .foregroundStyle(DSColor.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(DSSpacing.md)
                    }
                    .background(DSColor.surfaceRaised)
                }
            }

            Divider()

            // Error
            if let error = model.saveError {
                Text(error)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, DSSpacing.lg)
                    .padding(.top, DSSpacing.xs)
            }

            // Footer
            HStack {
                Spacer()
                Button("Cancel") { dismiss() }
                    .buttonStyle(.plain)
                    .foregroundStyle(DSColor.textSecondary)
                    .padding(.horizontal, DSSpacing.md)
                    .padding(.vertical, DSSpacing.xs)
                    .background(DSColor.surfaceOverlay, in: RoundedRectangle(cornerRadius: DSRadius.md))
                    .overlay(
                        RoundedRectangle(cornerRadius: DSRadius.md)
                            .stroke(DSColor.borderDefault, lineWidth: 1)
                    )

                Button("Save") {
                    Task {
                        if await model.save() { dismiss() }
                    }
                }
                .buttonStyle(.plain)
                .foregroundStyle(model.isDirty ? DSColor.buttonPrimaryText : DSColor.textMuted)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.xs)
                .background(
                    model.isDirty ? DSColor.buttonPrimaryBg : DSColor.surfaceOverlay,
                    in: RoundedRectangle(cornerRadius: DSRadius.md)
                )
                .disabled(!model.isDirty || model.isSaving)
            }
            .padding(.horizontal, DSSpacing.lg)
            .padding(.vertical, DSSpacing.md)
        }
        .background(DSColor.surfaceBase)
        .frame(width: 900, height: 600)
        .onAppear {
            if vm == nil { vm = SpecEditorViewModel(specFile: specFile) }
        }
    }
}
