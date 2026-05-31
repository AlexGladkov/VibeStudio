// MARK: - ProjectHeaderRow
// Unified header row for a project in either Files or Git sidebar section.
// macOS 14+, Swift 5.10

import SwiftUI

/// Configuration for the leading icon rendered before the project name.
struct ProjectHeaderIcon {
    let systemName: String
    let color: Color
    let font: Font
}

/// Unified header row for a project in any sidebar section.
///
/// Replaces the duplicated `ProjectFileHeaderView` and `ProjectGitHeaderView`
/// in `SidebarView.swift`. Section-specific content (branch info,
/// ahead/behind badges, …) is provided via the `trailing` slot. The gear
/// (settings) button is rendered when `onSettings != nil`.
struct ProjectHeaderRow<Trailing: View>: View {

    let project: Project
    let isActive: Bool
    let isExpanded: Bool
    let icon: ProjectHeaderIcon
    let remoteURL: String?
    let onTap: () -> Void
    /// Optional settings/gear button action — when non-nil renders gear next to row.
    var onSettings: (() -> Void)?
    /// Horizontal padding for the trailing gear button (Files uses `xs` after,
    /// Git uses `xs` before — passed by caller via the matching modifier).
    var gearTrailingPadding: CGFloat = DSSpacing.xs
    var gearLeadingPadding: CGFloat = 0
    let onRevealInFinder: () -> Void
    let onOpenInBrowser: () -> Void
    let onRemove: () -> Void
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onTap) {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "chevron.right")
                        .font(DSFont.iconSM)
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(isExpanded ? .degrees(90) : .zero)
                        .animation(.easeOut(duration: 0.15), value: isExpanded)

                    Image(systemName: icon.systemName)
                        .font(icon.font)
                        .foregroundStyle(icon.color)

                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(isActive ? DSColor.textPrimary : DSColor.textSecondary)
                        .fontWeight(isActive ? .medium : .regular)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    Spacer()

                    trailing()
                }
                .padding(.vertical, DSSpacing.xs)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if let onSettings {
                Button(action: onSettings) {
                    Image(systemName: "gearshape")
                        .font(DSFont.iconBase)
                        .foregroundStyle(DSColor.textMuted)
                        .frame(width: DSLayout.sidebarActionButtonSize, height: DSLayout.sidebarActionButtonSize)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.leading, gearLeadingPadding)
                .padding(.trailing, gearTrailingPadding)
                .help("Project settings")
            }
        }
        .contentShape(Rectangle())
        .projectContextMenu(
            remoteURL: remoteURL,
            onRevealInFinder: onRevealInFinder,
            onOpenInBrowser: onOpenInBrowser,
            onRemove: onRemove
        )
        .sidebarHover()
    }
}
