// MARK: - CommandSelectorView
// Dropdown + input row for selecting a codespeak command.
// macOS 14+, Swift 5.10

import SwiftUI

/// Compact command selector with a dropdown and an optional text input row.
///
/// The dropdown shows all ``CodeSpeakCommand`` cases. When the selected command
/// requires input (`.task` or `.change`), a text field appears below the menu.
struct CommandSelectorView: View {

    /// The currently selected command.
    @Binding var selectedCommand: CodeSpeakCommand

    /// Task name binding (used when `selectedCommand == .task`).
    @Binding var taskName: String

    /// Change message binding (used when `selectedCommand == .change`).
    @Binding var changeMessage: String

    /// Whether a command is currently running (disables the dropdown).
    let isRunning: Bool

    var body: some View {
        commandMenu()
    }

    // MARK: - Command Menu

    private func commandMenu() -> some View {
        Menu {
            ForEach(CodeSpeakCommand.allCases) { command in
                Button {
                    selectedCommand = command
                } label: {
                    Text(command.displayName)
                }
            }
        } label: {
            HStack(spacing: DSSpacing.xxs) {
                Text(selectedCommand.displayName)
                    .font(DSFont.sidebarSection)
                    .foregroundStyle(DSColor.textPrimary)
                Image(systemName: "chevron.down")
                    .font(.system(size: 8, weight: .semibold)) // sub-grid, intentionally micro
                    .foregroundStyle(DSColor.textMuted)
            }
        }
        .menuStyle(.borderlessButton)
        .fixedSize()
        .disabled(isRunning)
    }

    // MARK: - Input Row

    private func inputRow() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Text(selectedCommand.inputLabel)
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textSecondary)
                .frame(width: DSLayout.inputLabelWidth, alignment: .trailing)

            TextField(
                selectedCommand.inputPlaceholder,
                text: inputBinding
            )
            .textFieldStyle(.plain)
            .font(DSFont.sidebarItem)
            .foregroundStyle(DSColor.textPrimary)
            .padding(.horizontal, DSSpacing.xs)
            .padding(.vertical, DSSpacing.xxs)
            .background(
                DSColor.surfaceInput,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
        }
        .frame(height: DSLayout.inputRowHeight)
        .disabled(isRunning)
    }

    /// Switches the text field binding based on the selected command type.
    private var inputBinding: Binding<String> {
        switch selectedCommand {
        case .task:   return $taskName
        case .change: return $changeMessage
        default:      return .constant("")
        }
    }
}
