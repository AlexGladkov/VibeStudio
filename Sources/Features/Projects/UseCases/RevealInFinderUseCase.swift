// MARK: - RevealInFinderUseCase
// Open a folder in macOS Finder.
// macOS 14+, Swift 5.10

import AppKit
import Foundation
import OSLog

/// Reveals a project root in Finder.
///
/// Extracted from `SidebarView` so the view no longer reaches directly into
/// `NSWorkspace`. Wrapping the call in a use case keeps the AppKit dependency
/// behind a testable boundary (tests can stub the protocol if it grows in the
/// future).
@MainActor
struct RevealInFinderUseCase {

    /// Reveal the folder at `url` in Finder. The first argument to
    /// `selectFile(_:inFileViewerRootedAtPath:)` is `nil` so Finder opens the
    /// folder itself rather than selecting it inside its parent.
    ///
    /// - Parameter url: Absolute path to the directory to reveal.
    func execute(_ url: URL) {
        let path = url.path
        let opened = NSWorkspace.shared.selectFile(nil, inFileViewerRootedAtPath: path)
        if !opened {
            Logger.ui.warning("RevealInFinderUseCase: selectFile returned false for \(path, privacy: .private)")
        }
    }
}
