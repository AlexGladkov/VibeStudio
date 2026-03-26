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

    var body: some View {
        HStack(spacing: 0) {
            // Tab list.
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: DSSpacing.xxs) {
                    ForEach(projectManager.projects) { project in
                        TabItemView(
                            project: project,
                            isActive: project.id == projectManager.activeProjectId
                        )
                        .onTapGesture {
                            projectManager.activeProjectId = project.id
                        }
                    }
                }
                .padding(.horizontal, DSSpacing.sm)
            }

            Spacer()
        }
        .frame(height: DSLayout.tabBarHeight)
        .background(DSColor.surfaceTabBar)
    }
}
