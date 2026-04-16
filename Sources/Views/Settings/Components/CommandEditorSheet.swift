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

    @State private var content: String = ""
    @State private var savedContent: String = ""
    @State private var saveError: String?
    @State private var filename: String = ""

    private var isNewFile: Bool { fileURL == nil }
    private var hasUnsavedChanges: Bool { content != savedContent }

    // MARK: Constants

    private static let commandsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/commands")

    private static let newCommandTemplate = "# Название команды\n\nОписание что делает команда...\n"

    /// Characters allowed in a command filename.
    private static let filenameRegex = /^[a-z0-9_\-]+$/

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            toolbar
            Divider().background(DSColor.borderDefault)
            MarkdownEditorView(text: $content)
                .frame(maxWidth: .infinity, minHeight: 300, maxHeight: .infinity)
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
            if isNewFile {
                Text("Новая команда")
                    .font(DSFont.sheetTitle)
                    .foregroundStyle(DSColor.textPrimary)

                HStack(spacing: DSSpacing.xs) {
                    TextField("имя-файла", text: $filename)
                        .font(DSFont.monoSmall)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 160)

                    Text(".md")
                        .font(DSFont.monoSmall)
                        .foregroundStyle(DSColor.textMuted)
                }
            } else {
                Text(fileURL?.deletingPathExtension().lastPathComponent ?? "Команда")
                    .font(DSFont.sheetTitle)
                    .foregroundStyle(DSColor.textPrimary)

                if let url = fileURL {
                    Text(url.tildeAbbreviatedPath)
                        .font(DSFont.monoSmall)
                        .foregroundStyle(DSColor.textMuted)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
            }

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

            Button("Закрыть") {
                dismiss()
            }
            .buttonStyle(.bordered)
            .controlSize(.small)

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
        if let url = fileURL {
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            content = text
            savedContent = text
        } else {
            content = Self.newCommandTemplate
            savedContent = ""
        }
    }

    private func save() {
        saveError = nil

        if let url = fileURL {
            // Editing existing file — overwrite in place.
            do {
                try content.write(to: url, atomically: true, encoding: .utf8)
                savedContent = content
                onSaved?()
            } catch {
                saveError = "Ошибка: \(error.localizedDescription)"
            }
        } else {
            // New command — validate filename, write new file.
            let trimmed = filename.trimmingCharacters(in: .whitespaces)
            guard !trimmed.isEmpty else {
                saveError = "Введите имя файла"
                return
            }
            guard (try? Self.filenameRegex.wholeMatch(in: trimmed)) != nil else {
                saveError = "Допустимы только строчные латинские буквы, цифры, дефис и подчёркивание"
                return
            }

            let dir = Self.commandsDirectoryURL
            let targetURL = dir.appendingPathComponent("\(trimmed).md")

            guard !FileManager.default.fileExists(atPath: targetURL.path) else {
                saveError = "Файл «\(trimmed).md» уже существует"
                return
            }

            do {
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                try content.write(to: targetURL, atomically: true, encoding: .utf8)
                savedContent = content
                onSaved?()
                dismiss()
            } catch {
                saveError = "Ошибка: \(error.localizedDescription)"
            }
        }
    }
}
