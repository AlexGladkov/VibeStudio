// MARK: - FreeTab
// Model for a project-free terminal tab.
// macOS 14+, Swift 5.10

import Foundation

/// A standalone terminal tab not bound to any project.
///
/// Each free tab gets its own ``UUID`` which is used as a sentinel
/// `projectId` throughout `TerminalService`. This avoids changing
/// any protocol signatures -- the service treats it as just another
/// project ID that happens not to exist in `ProjectStore`.
struct FreeTab: Identifiable, Sendable {
    let id: UUID
    var title: String

    init(id: UUID = UUID(), title: String = "Terminal") {
        self.id = id
        self.title = title
    }
}
