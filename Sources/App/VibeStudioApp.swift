// MARK: - VibeStudio App Entry Point
// SwiftUI App lifecycle with unified toolbar and AppKit integration.
// macOS 14+, Swift 5.10

import SwiftUI

/// Main entry point for VibeStudio.
///
/// Uses ``AppDelegate`` as the Composition Root via
/// `@NSApplicationDelegateAdaptor`. All services are injected
/// into the SwiftUI environment through ``ServiceContainer``.
///
/// The `.unified(showsTitle: false)` toolbar style merges the macOS
/// NSToolbar with the title-bar row, placing toolbar items at the same
/// vertical level as the traffic-light buttons — identical to Android Studio.
@main
struct VibeStudioApp: App {

    @NSApplicationDelegateAdaptor private var appDelegate: AppDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .injectServices(from: appDelegate.container)
                .background(WindowToolbarRemover(container: appDelegate.container))
        }
        .windowStyle(.hiddenTitleBar)
        .windowToolbarStyle(.unified(showsTitle: false))
        .defaultSize(
            width: DSLayout.windowDefaultWidth,
            height: DSLayout.windowDefaultHeight
        )
    }
}
