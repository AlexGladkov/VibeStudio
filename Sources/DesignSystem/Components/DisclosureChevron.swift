// MARK: - DisclosureChevron
// Reusable animated chevron for disclosure/expand controls.
// macOS 14+, Swift 5.10

import SwiftUI

/// An animated chevron icon for disclosure controls.
///
/// Replaces duplicated `Image(systemName: "chevron.right")` + `.font(.system(size: 9))` + `rotationEffect`.
///
/// Defaults match the file-tree inline chevron (no fixed frame width — the
/// chevron sits flush against the directory icon). The CodeSpeak spec list
/// uses a slightly larger glyph and reserves a fixed frame to align section
/// headers, so it passes a custom `font` and sets `useFixedFrame: true`.
///
/// Usage:
/// ```swift
/// DisclosureChevron(isExpanded: isExpanded)
/// DisclosureChevron(isExpanded: specsExpanded,
///                   font: DSFont.statusBadge,
///                   useFixedFrame: true)
/// ```
struct DisclosureChevron: View {
    let isExpanded: Bool

    /// SF Symbol font for the chevron. Defaults to `iconSM` (9pt) which
    /// matches the file-tree call-site.
    var font: Font = DSFont.iconSM

    /// When `true`, the chevron is constrained to
    /// ``DSLayout/chevronFrameWidth`` so adjacent labels align across rows.
    /// Defaults to `false` to preserve `FileTreeView`'s original inline
    /// behaviour (no fixed frame).
    var useFixedFrame: Bool = false

    var body: some View {
        Image(systemName: "chevron.right")
            .font(font)
            .foregroundStyle(DSColor.textMuted)
            .rotationEffect(.degrees(isExpanded ? 90 : 0))
            .animation(.easeInOut(duration: 0.15), value: isExpanded)
            .modifier(OptionalFixedWidth(width: useFixedFrame ? DSLayout.chevronFrameWidth : nil))
    }
}

/// Conditionally applies a fixed-width `frame` when `width != nil`.
private struct OptionalFixedWidth: ViewModifier {
    let width: CGFloat?

    func body(content: Content) -> some View {
        if let width {
            content.frame(width: width)
        } else {
            content
        }
    }
}
