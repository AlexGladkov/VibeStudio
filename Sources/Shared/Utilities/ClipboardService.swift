// MARK: - ClipboardService
// Thin wrapper over NSPasteboard for copy-to-clipboard operations.
// macOS 14+, Swift 5.10

import AppKit

/// Utility for copying text to the system clipboard.
///
/// Centralizes the `clearContents` + `setString` boilerplate that was
/// previously duplicated across context menus, settings panes, and
/// status views.
public enum ClipboardService {
    /// Copy a plain string to the general pasteboard.
    ///
    /// Atomically clears existing contents before writing — same semantics
    /// as the direct `NSPasteboard` call sites it replaces.
    ///
    /// - Parameter text: The string to place on the pasteboard.
    public static func copy(_ text: String) {
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(text, forType: .string)
    }
}
