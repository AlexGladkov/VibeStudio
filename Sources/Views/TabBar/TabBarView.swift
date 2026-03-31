// MARK: - TabBarView
// Horizontal tab bar for project switching.
// Height: 36pt, drag-to-reorder, activity indicators.
// macOS 14+, Swift 5.10

import SwiftUI

/// Horizontal tab bar displaying one tab per open project.
///
/// Features:
/// - Activity indicator dots (idle/running/error)
/// - Branch name display
/// - Drag-to-reorder tabs
/// - Close button per tab
struct TabBarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.freeTabStore) private var freeTabStore

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: DSSpacing.xxs) {
                ForEach(projectManager.projects) { project in
                    TabItemView(
                        project: project,
                        isActive: project.id == projectManager.activeProjectId
                    )
                    .onTapGesture {
                        terminalManager.markProjectSeen(project.id)
                        projectManager.activeProjectId = project.id
                    }
                }

                ForEach(freeTabStore.freeTabs) { freeTab in
                    FreeTabItemView(
                        freeTab: freeTab,
                        isActive: projectManager.activeProjectId == freeTab.id
                    )
                    .onTapGesture {
                        projectManager.activeProjectId = freeTab.id
                    }
                }

                addFreeTabButton
            }
            .padding(.horizontal, DSSpacing.sm)
        }
        .frame(height: DSLayout.tabBarHeight)
        .background(DSColor.surfaceTabBar)
    }

    // MARK: - Private

    private var addFreeTabButton: some View {
        Button {
            let freeTab = freeTabStore.createFreeTab()
            projectManager.activeProjectId = freeTab.id
            // Session creation is handled by TerminalAreaView.emptyTerminalView.onAppear
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(DSColor.textSecondary)
                .frame(
                    width: DSLayout.tabAddButtonSize,
                    height: DSLayout.tabAddButtonSize
                )
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help("New Terminal")
    }
}
