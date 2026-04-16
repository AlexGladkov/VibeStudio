// MARK: - FreeTabItemView
// Individual tab in the tab bar for a project-free terminal.
// Shows terminal icon, title, close button.
// macOS 14+, Swift 5.10

import SwiftUI

/// A single tab representing a project-free terminal session.
///
/// Visually consistent with ``TabItemView`` but without git/project
/// metadata. Displays a terminal icon and an auto-numbered title.
struct FreeTabItemView: View {

    let freeTab: FreeTab
    let isActive: Bool

    @Environment(\.freeTabStore) private var freeTabStore
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.projectManager) private var projectManager
    @State private var isHovering = false

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "terminal")
                .font(DSFont.iconMD)
                .foregroundStyle(isActive || isHovering ? DSColor.textPrimary : DSColor.textSecondary)

            Text(freeTab.title)
                .font(DSFont.tabTitle)
                .foregroundStyle(isActive || isHovering ? DSColor.textPrimary : DSColor.textSecondary)
                .lineLimit(1)

            if isActive || isHovering {
                Button {
                    closeFreeTab()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: DSLayout.tabCloseIconSize))
                        .foregroundStyle(DSColor.textSecondary)
                        .opacity(isActive ? 0.6 : 0.4)
                        .frame(
                            width: DSLayout.tabCloseSize,
                            height: DSLayout.tabCloseSize
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, DSLayout.tabHorizontalPadding)
        .frame(
            minWidth: DSLayout.tabMinWidth,
            maxWidth: DSLayout.tabMaxWidth,
            minHeight: DSLayout.tabHeight,
            maxHeight: DSLayout.tabHeight
        )
        .background(tabBackground)
        .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
        .overlay(alignment: .bottom) {
            if isActive {
                Rectangle()
                    .fill(DSColor.accentPrimary)
                    .frame(height: DSLayout.tabActiveIndicatorHeight)
            }
        }
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.08)) {
                isHovering = hovering
            }
        }
    }

    // MARK: - Private

    private var tabBackground: Color {
        if isActive {
            return DSColor.surfaceTabActive
        } else if isHovering {
            return DSColor.surfaceTabHover
        } else {
            return DSColor.surfaceTabInactive
        }
    }

    private func closeFreeTab() {
        terminalManager.killAllSessions(for: freeTab.id)
        let nextId = freeTabStore.nextActiveId(
            after: freeTab.id,
            projects: projectManager.projects
        )
        freeTabStore.removeFreeTab(freeTab.id)
        projectManager.activeProjectId = nextId
    }
}
