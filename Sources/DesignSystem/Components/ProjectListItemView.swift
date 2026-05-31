// MARK: - ProjectListItemView
// Unified project row used in AddProjectPopover and WelcomeView.
// macOS 14+, Swift 5.10

import SwiftUI

/// A single project row showing folder icon, name, and path.
///
/// Layout pattern shared by:
/// - `AddProjectPopover.RecentRow` — uses `trailing` to show last-opened time
/// - `WelcomeView.RecentProjectRow` — uses no trailing content
///
/// Hover highlighting is provided by `.sidebarHover()` modifier, so this
/// component itself remains state-less.
struct ProjectListItemView<Trailing: View>: View {

    let project: Project
    let onTap: () -> Void
    /// Optional trailing content rendered to the right of the path row.
    @ViewBuilder var trailing: () -> Trailing

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: DSSpacing.sm) {
                Image(systemName: "folder.fill")
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(DSColor.gitModified)

                VStack(alignment: .leading, spacing: DSSpacing.xxs) {
                    Text(project.name)
                        .font(DSFont.sidebarItem)
                        .foregroundStyle(DSColor.textPrimary)
                        .lineLimit(1)

                    HStack(spacing: DSSpacing.xs) {
                        Text(NSString(string: project.path.path).abbreviatingWithTildeInPath)
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                            .lineLimit(1)
                            .truncationMode(.middle)

                        Spacer()

                        trailing()
                    }
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sidebarHover(cornerRadius: DSRadius.sm)
    }
}

extension ProjectListItemView where Trailing == EmptyView {
    /// Convenience initializer for rows without trailing content.
    init(project: Project, onTap: @escaping () -> Void) {
        self.init(project: project, onTap: onTap, trailing: { EmptyView() })
    }
}
