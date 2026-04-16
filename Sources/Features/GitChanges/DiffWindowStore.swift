// MARK: - DiffWindowStore
// Manages standalone diff NSWindow instances.
// macOS 14+, Swift 5.10

import AppKit
import SwiftUI

/// Manages standalone diff `NSWindow` instances.
///
/// Retains windows in a static array so they are not deallocated while open.
/// Each window is removed from the array automatically when it closes.
@MainActor
enum DiffWindowStore {

    private static var windows: [NSWindow] = []

    /// Open a new resizable diff window for the given file.
    static func open(
        file: GitFile,
        staged: Bool,
        projectPath: URL?,
        gitService: any GitServicing
    ) {
        let content = FileDiffSheetView(
            file: file,
            staged: staged,
            projectPath: projectPath,
            gitService: gitService
        )

        let vc = NSHostingController(rootView: content)
        let window = NSWindow(contentViewController: vc)
        window.styleMask = [.titled, .closable, .resizable, .miniaturizable]
        window.title = (file.path as NSString).lastPathComponent

        let screen = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
        let width  = min(screen.width  * 0.82, 1680)
        let height = min(screen.height * 0.88, 1100)
        window.setContentSize(NSSize(width: width, height: height))
        window.center()

        window.minSize = NSSize(width: 760, height: 480)
        window.isReleasedWhenClosed = false

        windows.append(window)

        NotificationCenter.default.addObserver(
            forName: NSWindow.willCloseNotification,
            object: window,
            queue: .main
        ) { [weak window] _ in
            guard let window else { return }
            MainActor.assumeIsolated {
                DiffWindowStore.windows.removeAll { $0 === window }
            }
        }

        window.makeKeyAndOrderFront(nil)
    }
}
