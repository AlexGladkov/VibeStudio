// MARK: - FreeTabManaging
// Protocol for managing project-free terminal tabs.
// macOS 14+, Swift 5.10

import Foundation

/// Describes the interface for managing project-free terminal tabs.
///
/// ``FreeTabStore`` is the production implementation.
/// This protocol exists to enable test doubles without changing the DI container
/// (which must hold the concrete `@Observable` type for SwiftUI tracking).
///
/// - Note: `@MainActor` is required because all mutations affect SwiftUI-observed state
///   and must run on the main thread.
@MainActor
protocol FreeTabManaging: AnyObject, Observable {

    /// All currently open free tabs, in display order.
    var freeTabs: [FreeTab] { get }

    /// Creates a new free terminal tab with an auto-numbered title.
    ///
    /// - Returns: The newly created ``FreeTab``.
    @discardableResult
    func createFreeTab() -> FreeTab

    /// Removes a free tab by its identifier.
    ///
    /// - Parameter id: The ``UUID`` of the tab to remove.
    func removeFreeTab(_ id: UUID)

    /// Returns `true` when the given identifier belongs to a free tab.
    ///
    /// - Parameter id: The identifier to check.
    func isFreeTab(_ id: UUID) -> Bool

    /// Suggests the next active ID after closing the tab with `removedId`.
    ///
    /// Priority: another free tab (last in list) -> first project.
    /// Returns `nil` when nothing is left to activate.
    ///
    /// - Parameters:
    ///   - removedId: The identifier of the tab being closed.
    ///   - projects: The current list of projects to fall back to.
    func nextActiveId(after removedId: UUID, projects: [Project]) -> UUID?
}
