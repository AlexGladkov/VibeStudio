// MARK: - TerminalSessionStore
// Manages terminal view cache, session-project index, and session CRUD.
// Internal helper for TerminalService -- not exposed outside the Terminal module.
// macOS 14+, Swift 5.10

import AppKit
import OSLog
import SwiftTerm

/// Stores terminal views and session bookkeeping for ``TerminalService``.
///
/// This type is intentionally **not** `@Observable` -- the facade
/// (`TerminalService`) owns the `@Observable` contract. The store merely
/// holds state and provides mutation helpers.
@MainActor
final class TerminalSessionStore {

    // MARK: - State

    /// Cache of SwiftTerm views keyed by session ID.
    private(set) var terminalViews: [UUID: TaggedTerminalView] = [:]

    /// O(1) reverse index: sessionId -> projectId.
    private(set) var sessionProjectIndex: [UUID: UUID] = [:]

    // MARK: - View Management

    /// Register a terminal view for a session in a given project.
    func register(
        view: TaggedTerminalView,
        sessionId: UUID,
        projectId: UUID
    ) {
        terminalViews[sessionId] = view
        sessionProjectIndex[sessionId] = projectId
    }

    /// Retrieve the cached view for a session.
    func view(for sessionId: UUID) -> TaggedTerminalView? {
        terminalViews[sessionId]
    }

    /// Remove a view from the cache without touching session bookkeeping.
    @discardableResult
    func removeView(for sessionId: UUID) -> TaggedTerminalView? {
        terminalViews.removeValue(forKey: sessionId)
    }

    /// All currently cached views (for bulk operations like theme refresh).
    var allViews: Dictionary<UUID, TaggedTerminalView>.Values {
        terminalViews.values
    }

    /// The number of currently cached views.
    var viewCount: Int {
        terminalViews.count
    }

    // MARK: - Session Index

    /// Look up the project ID that owns a given session.
    func projectId(for sessionId: UUID) -> UUID? {
        sessionProjectIndex[sessionId]
    }

    /// Remove the session-project index entry.
    @discardableResult
    func removeProjectIndex(for sessionId: UUID) -> UUID? {
        sessionProjectIndex.removeValue(forKey: sessionId)
    }

    /// Remove all tracking state for a session.
    ///
    /// Removes the view, the session-project index entry, and returns the
    /// owning project ID (if any) so the caller can update session lists.
    ///
    /// - Returns: The project ID that owned this session, or `nil`.
    @discardableResult
    func removeAll(for sessionId: UUID) -> UUID? {
        terminalViews.removeValue(forKey: sessionId)
        return sessionProjectIndex.removeValue(forKey: sessionId)
    }
}
