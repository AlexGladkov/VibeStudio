// MARK: - FreeTabStore
// Service managing project-free terminal tabs.
// macOS 14+, Swift 5.10

import Foundation
import Observation

// MARK: - FreeTabManaging Conformance

extension FreeTabStore: FreeTabManaging {}

/// Manages the lifecycle of free (project-independent) terminal tabs.
///
/// Each free tab is assigned a unique ``UUID`` that acts as a sentinel
/// `projectId` within `TerminalService`. Since `ProjectStore` knows
/// nothing about these IDs, `TerminalService.createSession` falls
/// through to `NSHomeDirectory()` for the working directory and the
/// user's default shell -- exactly the desired behavior.
@Observable
@MainActor
final class FreeTabStore {

    // MARK: - State

    /// All currently open free tabs, in display order.
    private(set) var freeTabs: [FreeTab] = []

    // MARK: - Actions

    /// Creates a new free terminal tab with an auto-numbered title.
    ///
    /// - Returns: The newly created ``FreeTab``.
    @discardableResult
    func createFreeTab() -> FreeTab {
        let count = freeTabs.count + 1
        let title = count == 1 ? "Terminal" : "Terminal \(count)"
        let tab = FreeTab(title: title)
        freeTabs.append(tab)
        return tab
    }

    /// Removes a free tab by its identifier.
    func removeFreeTab(_ id: UUID) {
        freeTabs.removeAll { $0.id == id }
    }

    /// Returns `true` when the given identifier belongs to a free tab.
    func isFreeTab(_ id: UUID) -> Bool {
        freeTabs.contains { $0.id == id }
    }

    /// Suggests the next active ID after closing the tab with `removedId`.
    ///
    /// Priority: another free tab (last in list) -> first project.
    /// Returns `nil` when nothing is left to activate.
    func nextActiveId(after removedId: UUID, projects: [Project]) -> UUID? {
        let remaining = freeTabs.filter { $0.id != removedId }
        if let next = remaining.last { return next.id }
        return projects.first?.id
    }
}
