// MARK: - TabBarView
// Horizontal tab bar for project switching.
// Height: 36pt, drag-to-reorder, activity indicators.
// macOS 14+, Swift 5.10

import SwiftUI
import UniformTypeIdentifiers

// MARK: - DropDelegate helpers

/// Handles drop events for project tabs.
///
/// Projects reorder only among themselves; cross-group drops are rejected.
private struct ProjectTabDropDelegate: DropDelegate {

    let targetProject: Project
    let projects: [Project]
    let draggingId: Binding<UUID?>
    let onMove: (IndexSet, Int) -> Void

    func performDrop(info: DropInfo) -> Bool {
        draggingId.wrappedValue = nil
        return true
    }

    func dropEntered(info: DropInfo) {
        guard
            let draggedId = draggingId.wrappedValue,
            let fromIndex = projects.firstIndex(where: { $0.id == draggedId }),
            let toIndex   = projects.firstIndex(where: { $0.id == targetProject.id }),
            fromIndex != toIndex
        else { return }

        // Adjust destination offset to match Array.move semantics.
        let destination = toIndex > fromIndex ? toIndex + 1 : toIndex
        onMove(IndexSet(integer: fromIndex), destination)
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    /// Reject drops whose payload UUID belongs to a free tab (not a project).
    func validateDrop(info: DropInfo) -> Bool {
        guard let draggedId = draggingId.wrappedValue else { return false }
        return projects.contains { $0.id == draggedId }
    }
}

/// Handles drop events for free tabs.
///
/// Free tabs reorder only among themselves; cross-group drops are rejected.
private struct FreeTabDropDelegate: DropDelegate {

    let targetFreeTab: FreeTab
    let freeTabs: [FreeTab]
    let draggingId: Binding<UUID?>
    let onMove: (IndexSet, Int) -> Void

    func performDrop(info: DropInfo) -> Bool {
        draggingId.wrappedValue = nil
        return true
    }

    func dropEntered(info: DropInfo) {
        guard
            let draggedId = draggingId.wrappedValue,
            let fromIndex = freeTabs.firstIndex(where: { $0.id == draggedId }),
            let toIndex   = freeTabs.firstIndex(where: { $0.id == targetFreeTab.id }),
            fromIndex != toIndex
        else { return }

        let destination = toIndex > fromIndex ? toIndex + 1 : toIndex
        onMove(IndexSet(integer: fromIndex), destination)
    }

    func dropUpdated(info: DropInfo) -> DropProposal? {
        DropProposal(operation: .move)
    }

    func validateDrop(info: DropInfo) -> Bool {
        guard let draggedId = draggingId.wrappedValue else { return false }
        return freeTabs.contains { $0.id == draggedId }
    }
}

// MARK: - TabBarView

/// Horizontal tab bar displaying one tab per open project plus free tabs.
///
/// Features:
/// - Activity indicator dots (idle/running/error)
/// - Branch name display
/// - Drag-to-reorder tabs (within each group)
/// - Close button per tab
struct TabBarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.freeTabStore) private var freeTabStore

    /// UUID of the tab currently being dragged; `nil` when no drag is active.
    @State private var draggingId: UUID?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: DSSpacing.xxs) {
                ForEach(projectManager.projects) { project in
                    TabItemView(
                        project: project,
                        isActive: project.id == projectManager.activeProjectId
                    )
                    .opacity(draggingId == project.id ? 0.5 : 1.0)
                    .onTapGesture {
                        terminalManager.markProjectSeen(project.id)
                        projectManager.activeProjectId = project.id
                    }
                    .onDrag {
                        draggingId = project.id
                        return NSItemProvider(object: project.id.uuidString as NSString)
                    }
                    .onDrop(
                        of: [.text],
                        delegate: ProjectTabDropDelegate(
                            targetProject: project,
                            projects: projectManager.projects,
                            draggingId: $draggingId,
                            onMove: { source, destination in
                                projectManager.moveProjects(from: source, to: destination)
                            }
                        )
                    )
                }

                ForEach(freeTabStore.freeTabs) { freeTab in
                    FreeTabItemView(
                        freeTab: freeTab,
                        isActive: projectManager.activeProjectId == freeTab.id
                    )
                    .opacity(draggingId == freeTab.id ? 0.5 : 1.0)
                    .onTapGesture {
                        projectManager.activeProjectId = freeTab.id
                    }
                    .onDrag {
                        draggingId = freeTab.id
                        return NSItemProvider(object: freeTab.id.uuidString as NSString)
                    }
                    .onDrop(
                        of: [.text],
                        delegate: FreeTabDropDelegate(
                            targetFreeTab: freeTab,
                            freeTabs: freeTabStore.freeTabs,
                            draggingId: $draggingId,
                            onMove: { source, destination in
                                freeTabStore.moveFreeTabs(from: source, to: destination)
                            }
                        )
                    )
                }

                addFreeTabButton
            }
            .padding(.horizontal, DSSpacing.sm)
        }
        .frame(height: DSLayout.tabBarHeight)
        .background(DSColor.surfaceTabBar)
        // Catch-all drop zone: resets draggingId when the drag session ends
        // outside every tab's own DropDelegate (e.g. empty space, outside window).
        // Returns false so the drop is not consumed and the OS can still cancel it.
        .onDrop(of: [.text], isTargeted: nil) { _ in
            draggingId = nil
            return false
        }
    }

    // MARK: - Private

    private var addFreeTabButton: some View {
        Button {
            let freeTab = freeTabStore.createFreeTab()
            projectManager.activeProjectId = freeTab.id
            // Session creation is handled by TerminalAreaView.emptyTerminalView.onAppear
        } label: {
            Image(systemName: "plus")
                .font(DSFont.tabTitle)
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
