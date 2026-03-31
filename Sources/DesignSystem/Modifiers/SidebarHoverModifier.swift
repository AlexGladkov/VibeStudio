// MARK: - SidebarHoverModifier
// Reusable hover highlight modifier for sidebar interactive rows.
// macOS 14+, Swift 5.10

import SwiftUI

/// Adds a subtle background highlight on mouse hover for sidebar interactive rows.
struct SidebarHoverModifier: ViewModifier {
    let cornerRadius: CGFloat

    @State private var isHovering = false

    init(cornerRadius: CGFloat = 4) {
        self.cornerRadius = cornerRadius
    }

    func body(content: Content) -> some View {
        content
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(isHovering ? DSColor.textPrimary.opacity(0.07) : Color.clear)
            )
            .onHover { hovering in
                isHovering = hovering
            }
    }
}

extension View {
    /// Applies a sidebar hover highlight effect.
    func sidebarHover(cornerRadius: CGFloat = 4) -> some View {
        modifier(SidebarHoverModifier(cornerRadius: cornerRadius))
    }
}
