// MARK: - ToolbarView
// Orchestrator that picks between Regular and CodeSpeak toolbars.
// Concrete UI lives in `RegularToolbarView` and `CodeSpeakToolbarView`.
// macOS 14+, Swift 5.10

import SwiftUI

/// Android Studio-style run-configuration toolbar above the tab bar.
///
/// Layout switches based on `AppMode`:
/// - **Regular** mode → `RegularToolbarView`
/// - **CodeSpeak** mode → `CodeSpeakToolbarView`
///
/// Hidden entirely when the welcome screen is shown (no projects + no free
/// tabs).
///
/// Was a 770-LOC god view (ARCH-H6). Concrete UI was extracted into
/// section-specific files. This orchestrator only owns the mode branching and
/// the `ToolbarViewModel` lifecycle.
struct ToolbarView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @Environment(\.agentAvailability) private var agentAvailability
    @Environment(\.freeTabStore) private var freeTabStore
    @Environment(\.costTrackerService) private var costTrackerService
    // Observable-style injection — required for currentMode reactive tracking.
    @Environment(AppNavigationCoordinator.self) private var navigationCoordinator

    @State private var vmBox = LazyStateObject<ToolbarViewModel>()

    /// Lazily-created VM. Built on first access with environment services
    /// injected at construction time. `LazyStateObject` avoids the historical
    /// "Task { @MainActor in vm = created }" anti-pattern (ARCH-H10).
    private var vm: ToolbarViewModel {
        vmBox.resolve {
            ToolbarViewModel(
                projectManager: projectManager,
                terminalManager: terminalManager,
                agentAvailability: agentAvailability,
                costTrackerService: costTrackerService
            )
        }
    }

    var body: some View {
        let isWelcomeScreen = (projectManager.projects.isEmpty || projectManager.activeProjectId == nil)
            && freeTabStore.freeTabs.isEmpty

        Group {
            if !isWelcomeScreen {
                if navigationCoordinator.currentMode == .codeSpeak {
                    CodeSpeakToolbarView()
                } else {
                    RegularToolbarView(model: vm)
                }
            }
        }
    }
}
