// MARK: - ProjectContextMenuModifier
// Shared context menu for project header rows (Files / Git sections).
// macOS 14+, Swift 5.10

import SwiftUI

/// Standard three-item project context menu: Reveal in Finder, Open in
/// GitHub/GitLab (when remote URL is known), Remove from VibeStudio.
struct ProjectContextMenuModifier: ViewModifier {

    let remoteURL: String?
    let onRevealInFinder: () -> Void
    let onOpenInBrowser: () -> Void
    let onRemove: () -> Void

    func body(content: Content) -> some View {
        content.contextMenu {
            Button(action: onRevealInFinder) {
                Label("Reveal in Finder", systemImage: "folder")
            }

            if remoteURL != nil {
                Button(action: onOpenInBrowser) {
                    Label("Open in GitHub/GitLab", systemImage: "safari")
                }
            }

            Divider()

            Button(role: .destructive, action: onRemove) {
                Label("Remove from VibeStudio", systemImage: "xmark.circle")
            }
        }
    }
}

extension View {

    /// Attaches the standard project context menu (Reveal/GitHub/Remove).
    func projectContextMenu(
        remoteURL: String?,
        onRevealInFinder: @escaping () -> Void,
        onOpenInBrowser: @escaping () -> Void,
        onRemove: @escaping () -> Void
    ) -> some View {
        modifier(ProjectContextMenuModifier(
            remoteURL: remoteURL,
            onRevealInFinder: onRevealInFinder,
            onOpenInBrowser: onOpenInBrowser,
            onRemove: onRemove
        ))
    }
}
