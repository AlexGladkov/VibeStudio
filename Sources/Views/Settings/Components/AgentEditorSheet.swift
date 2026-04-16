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

    @State private var content: String = ""
    @State private var savedContent: String = ""
    @State private var saveError: String?

    private var hasUnsavedChanges: Bool { content != savedContent }

    /// Parsed agent name from frontmatter (for the toolbar title).
    private var agentName: String {
        let parsed = parseFrontmatter(content).name
        return parsed.isEmpty ? "Новый агент" : parsed
    }

    /// Subtitle shown below the agent name in the toolbar.
    private var agentSubtitle: String {
        guard let url = fileURL else { return "Новый файл" }
        return url.tildeAbbreviatedPath
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
            Text(agentName)
                .font(DSFont.sheetTitle)
                .foregroundStyle(DSColor.textPrimary)

            Text(agentSubtitle)
                .font(DSFont.monoSmall)
                .foregroundStyle(DSColor.textMuted)
                .lineLimit(1)
                .truncationMode(.middle)

            Spacer()

            Button {
                onDismiss?()
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

    private static let agentsDirectoryURL: URL = FileManager.default
        .homeDirectoryForCurrentUser
        .appendingPathComponent(".claude/agents")

    private static let newAgentTemplate = """
        ---
        name: my-agent
        description:
        model: sonnet
        color: blue
        ---

        Ты — агент, выполняющий...
        """

    private func load() {
        if let url = fileURL {
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            content = text
            savedContent = text
        } else {
            content = Self.newAgentTemplate
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
                onDismiss?()
            } catch {
                saveError = "Ошибка: \(error.localizedDescription)"
            }
        } else {
            // New agent — derive filename from frontmatter name:.
            let parsed = parseFrontmatter(content)
            let rawName = parsed.name
            guard !rawName.isEmpty else {
                saveError = "Укажите поле name в frontmatter"
                return
            }
            let sanitized = sanitizeAgentName(rawName)
            guard !sanitized.isEmpty else {
                saveError = "Недопустимое имя агента: используйте латинские буквы, цифры и дефисы"
                return
            }

            let dir = Self.agentsDirectoryURL
            let targetURL = dir.appendingPathComponent("\(sanitized).md")

            do {
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                try content.write(to: targetURL, atomically: true, encoding: .utf8)
                savedContent = content
                onDismiss?()
                dismiss()
            } catch {
                saveError = "Ошибка: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Name Sanitisation

    /// Converts an agent name to a safe filename component.
    ///
    /// Rules: lowercase, spaces replaced with dashes, only `[a-z0-9\-_]` allowed.
    private func sanitizeAgentName(_ name: String) -> String {
        name
            .lowercased()
            .replacingOccurrences(of: " ", with: "-")
            .filter { $0.isLetter && $0.isASCII || $0.isNumber || $0 == "-" || $0 == "_" }
    }
}

