// MARK: - TerminalSessionQuerying Protocol
// Session lookup, listing, events and activity marking.
// macOS 14+, Swift 5.10

import Foundation

/// Terminal session querying and activity observation.
///
/// Provides read access to sessions, activity events, and the ability
/// to mark a project's tab as seen (clearing the activity indicator).
@MainActor
protocol TerminalSessionQuerying: AnyObject, Observable {

    /// Все активные сессии, сгруппированные по проекту.
    /// Ключ -- projectId, значение -- массив сессий в порядке создания.
    var sessionsByProject: [UUID: [TerminalSession]] { get }

    /// Агрегированное состояние активности для каждого проекта.
    var projectActivityStates: [UUID: TabActivityState] { get }

    /// Получить сессию по ID.
    func session(for id: UUID) -> TerminalSession?

    /// Все сессии конкретного проекта.
    func sessions(for projectId: UUID) -> [TerminalSession]

    /// Подписка на события активности сессий (для индикаторов на табах).
    var sessionEvents: AsyncStream<TerminalSessionEvent> { get }

    /// Mark a project's tab as seen by the user.
    ///
    /// Resets the activity state to `.idle` so the yellow indicator clears.
    /// Call this when the user switches to a tab.
    func markProjectSeen(_ projectId: UUID)
}
