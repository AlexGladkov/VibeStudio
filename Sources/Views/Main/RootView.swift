// MARK: - RootView
// Top-level view composing Sidebar, TabBar, and Terminal area.
// macOS 14+, Swift 5.10

import SwiftUI

/// Root content view for VibeStudio.
///
/// Layout:
/// ```
/// ┌─────────────────────────────────────────────┐
/// │  [traffic lights]  [ToolbarView items]       │  ← native NSToolbar row
/// ├──────────────┬──────────────────────────────┤
/// │              │        TabBarView             │
/// │  SidebarView ├──────────────────────────────┤
/// │              │  TerminalAreaView / Welcome   │
/// └──────────────┴──────────────────────────────┘
/// ```
///
/// ToolbarView content lives in `.toolbar { }` so macOS places it in the
/// unified toolbar row at the same level as the traffic-light buttons,
/// identical to Android Studio / IntelliJ on macOS.
struct RootView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(AppReadyState.self) private var appReady
    @Environment(\.themeService) private var themeService
    @Environment(\.navigationCoordinator) private var navigationCoordinator
    @Environment(\.freeTabStore) private var freeTabStore
    @State private var showSidebar = true
    @State private var showSettings = false

    /// The concrete color scheme to apply to the entire window.
    ///
    /// Delegates to `themeService.resolvedColorScheme` which always returns
    /// `.dark` or `.light` — never `nil`. This avoids a one-frame race where
    /// passing `nil` would cause SwiftUI to inherit the color scheme from the
    /// NSWindow, whose KVO-based effective-appearance update fires asynchronously
    /// and can lag behind the synchronous `NSApp.appearance` change in ThemeService.
    private var preferredScheme: ColorScheme {
        themeService.resolvedColorScheme
    }

    var body: some View {
        Group {
            if !appReady.tccGranted {
                // Hold off rendering until TCC consent is obtained.
                // Any SwiftUI view that accesses ~/Documents (FileTreeView,
                // GitSidebarViewModel, etc.) must NOT render before this gate
                // opens — their .task modifiers spawn git child processes that
                // trigger independent TCC dialogs even if the parent already
                // has a pending consent dialog.
                DSColor.surfaceBase
            } else if projectManager.projects.isEmpty && freeTabStore.freeTabs.isEmpty {
                WelcomeView()
            } else {
                HSplitView {
                    SidebarView()
                        .frame(
                            minWidth: showSidebar ? DSLayout.sidebarMinWidth : 0,
                            idealWidth: showSidebar ? DSLayout.sidebarDefaultWidth : 0,
                            maxWidth: showSidebar ? DSLayout.sidebarMaxWidth : 0
                        )
                        .clipped()

                    VStack(spacing: 0) {
                        TabBarView()

                        if projectManager.activeProjectId != nil {
                            TerminalAreaView()
                        } else {
                            WelcomeView()
                        }
                    }
                    .frame(minWidth: 300)
                }
                .background {
                    Button("") {
                        withAnimation(.easeOut(duration: 0.2)) {
                            showSidebar.toggle()
                        }
                    }
                    .keyboardShortcut("b", modifiers: .command)
                    .hidden()
                }
            }
        }
        .toolbar {
            // Invisible 1 pt spacer — keeps the unified toolbar area alive so that
            // SwiftUI sets the correct top safe-area inset for the content below.
            // The actual controls (claude selector, run, globe) are mounted as an
            // NSHostingView on the trailing side via WindowToolbarRemover.
            ToolbarItem(placement: .automatic) {
                Color.clear.frame(width: 1, height: 1)
            }
        }
        .frame(
            minWidth: DSLayout.windowMinWidth,
            minHeight: DSLayout.windowMinHeight
        )
        .background(DSColor.surfaceBase)
        .preferredColorScheme(preferredScheme)
        .sheet(
            isPresented: Binding(
                get: { navigationCoordinator.agentToInstall != nil },
                set: { if !$0 { navigationCoordinator.agentToInstall = nil } }
            )
        ) {
            if let assistant = navigationCoordinator.agentToInstall {
                InstallAgentSheet(assistant: assistant)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView()
        }
        .onChange(of: navigationCoordinator.showingSettings) { _, newValue in
            if newValue {
                showSettings = true
                navigationCoordinator.showingSettings = false
            }
        }
    }
}
