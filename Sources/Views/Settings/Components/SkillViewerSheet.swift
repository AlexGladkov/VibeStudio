// MARK: - SkillViewerSheet
// Sheet for viewing (and optionally editing) a Claude skill from ~/.claude/skills/.
// macOS 14+, Swift 5.10

import SwiftUI

// MARK: - SkillInfo

/// Parsed representation of a single skill directory from `~/.claude/skills/`.
struct SkillInfo: Identifiable {
    /// Directory path used as stable identity.
    let id: String
    /// URL of the skill directory.
    let directoryURL: URL
    /// URL of the SKILL.md file inside the directory.
    let skillFileURL: URL
    /// Parsed `name:` from SKILL.md frontmatter.
    let name: String
    /// Parsed `description:` from SKILL.md frontmatter.
    let description: String
    /// Whether the skill declares `user_invocable: true`.
    let isUserInvocable: Bool
    /// `false` for symlinks or read-only files (e.g. Homebrew-installed skills).
    let isWritable: Bool
}

// MARK: - SkillViewerSheet

/// Modal sheet for viewing (and editing if writable) a skill's SKILL.md.
///
/// When the skill is read-only (symlink or Homebrew-installed), the editor
/// is non-editable and the save button is hidden.
struct SkillViewerSheet: View {

    // MARK: Init

    /// Skill to display.
    let skill: SkillInfo
    /// Called when the sheet is dismissed so the caller can refresh its skill list.
    var onDismiss: (() -> Void)?

    // MARK: State

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
            MarkdownEditorView(text: $content, isEditable: skill.isWritable)
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
            Text(skill.name.isEmpty ? skill.directoryURL.lastPathComponent : skill.name)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(DSColor.textPrimary)

            if !skill.isWritable {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(DSColor.textMuted)

                    Text("Только чтение")
                        .font(.system(size: 11))
                        .foregroundStyle(DSColor.textMuted)
                }
            }

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
            if !skill.isWritable {
                Text("Скилл установлен через Homebrew, файл только для чтения")
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.textMuted)
            } else if let err = saveError {
                Text(err)
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.gitDeleted)
            } else if hasUnsavedChanges {
                Text("Есть несохранённые изменения")
                    .font(.system(size: 11))
                    .foregroundStyle(DSColor.textMuted)
            }

            Spacer()

            if skill.isWritable {
                Button("Сохранить", action: save)
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

    // MARK: - File I/O

    private func load() {
        let text = (try? String(contentsOf: skill.skillFileURL, encoding: .utf8)) ?? ""
        content = text
        savedContent = text
    }

    private func save() {
        saveError = nil
        do {
            try content.write(to: skill.skillFileURL, atomically: true, encoding: .utf8)
            savedContent = content
            onDismiss?()
        } catch {
            saveError = "Ошибка: \(error.localizedDescription)"
        }
    }
}
