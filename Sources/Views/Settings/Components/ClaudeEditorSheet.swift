// MARK: - ClaudeEditorSheet
// Editable sheet for ~/.claude/CLAUDE.md.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - ClaudeEditorSheet

/// Modal sheet providing a full-height editable NSTextView for CLAUDE.md.
struct ClaudeEditorSheet: View {

    @Environment(\.dismiss) private var dismiss

    @State private var content: String = ""
    @State private var savedContent: String = ""
    @State private var saveError: String?

    private var hasUnsavedChanges: Bool { content != savedContent }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().background(DSColor.borderDefault)
            MarkdownEditorView(text: $content)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Divider().background(DSColor.borderDefault)
            bottomBar
        }
        .frame(width: 680, height: 520)
        .background(DSColor.surfaceDefault)
        .onAppear(perform: load)
    }

    // MARK: - Toolbar

    private var toolbar: some View {
        HStack(spacing: DSSpacing.sm) {
            Text("CLAUDE.md")
                .font(DSFont.sheetTitle)
                .foregroundStyle(DSColor.textPrimary)

            Text("~/.claude/CLAUDE.md")
                .font(DSFont.monoSmall)
                .foregroundStyle(DSColor.textMuted)

            Spacer()

            Button {
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(DSFont.bodyMedium)
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .keyboardShortcut(.escape, modifiers: [])
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - Bottom Bar

    private var bottomBar: some View {
        HStack(spacing: DSSpacing.sm) {
            if let err = saveError {
                Text(err)
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.gitDeleted)
            } else if hasUnsavedChanges {
                Text("Есть несохранённые изменения")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()

            Button("Сохранить", action: save)
                .keyboardShortcut("s", modifiers: .command)
                .disabled(!hasUnsavedChanges)
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
        }
        .padding(.horizontal, DSSpacing.lg)
        .padding(.vertical, DSSpacing.sm)
        .background(DSColor.surfaceRaised)
    }

    // MARK: - File I/O

    private static let fileURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/CLAUDE.md")

    private func load() {
        let text = (try? String(contentsOf: Self.fileURL, encoding: .utf8)) ?? ""
        content = text
        savedContent = text
    }

    private func save() {
        saveError = nil
        do {
            let dir = Self.fileURL.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try content.write(to: Self.fileURL, atomically: true, encoding: .utf8)
            savedContent = content
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
        }
    }
}

