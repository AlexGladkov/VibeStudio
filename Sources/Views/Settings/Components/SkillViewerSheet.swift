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
    @State private var vmBox = LazyStateObject<FileEditorViewModel>()

    private var vm: FileEditorViewModel {
        vmBox.resolve {
            FileEditorViewModel(fileURL: skill.skillFileURL, createsDirectory: false)
        }
    }

    private var title: String {
        skill.name.isEmpty ? skill.directoryURL.lastPathComponent : skill.name
    }

    // MARK: - Body

    var body: some View {
        let model = vm

        EditorSheetScaffold(
            title: title,
            readOnly: !skill.isWritable,
            readOnlyMessage: "Skill installed via Homebrew, file is read-only",
            hasUnsavedChanges: model.hasUnsavedChanges,
            saveError: model.saveError,
            minEditorHeight: 300,
            content: Binding(get: { model.content }, set: { model.content = $0 }),
            onClose: {
                onDismiss?()
                dismiss()
            },
            onSave: {
                if model.save() { onDismiss?() }
            },
            toolbarAccessory: {
                if !skill.isWritable {
                    HStack(spacing: DSSpacing.xs) {
                        Image(systemName: "lock.fill")
                            .font(DSFont.iconMD)
                            .foregroundStyle(DSColor.textMuted)

                        Text("Read-only")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }
                }
            }
        )
        .onAppear { model.load() }
    }
}
