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

    @State private var content: String = ""
    @State private var savedContent: String = ""
    @State private var saveError: String?

    private var hasUnsavedChanges: Bool { content != savedContent }

    private var displayPath: String {
        fileURL.tildeAbbreviatedPath
    }

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
            Text(displayTitle)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(DSColor.textPrimary)

            Text(displayPath)
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(DSColor.textMuted)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer()

            Button {
                onDismiss?()
                dismiss()
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 14))
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
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.gitDeleted)
            } else if hasUnsavedChanges {
                Text("Есть несохранённые изменения")
                    .font(.system(size: 11))
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

    private func load() {
        let text = (try? String(contentsOf: fileURL, encoding: .utf8)) ?? defaultContent
        content = text
        savedContent = text
    }

    private func save() {
        saveError = nil
        do {
            let dir = fileURL.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try content.write(to: fileURL, atomically: true, encoding: .utf8)
            savedContent = content
            onDismiss?()
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
        }
    }
}
