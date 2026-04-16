// MARK: - DisclosureChevron
// Reusable animated chevron for disclosure/expand controls.
// macOS 14+, Swift 5.10

import SwiftUI

/// An animated chevron icon for disclosure controls.
///
/// Replaces duplicated `Image(systemName: "chevron.right")` + `.font(.system(size: 9))` + `rotationEffect`.
///
/// Usage:
/// ```swift
/// DisclosureChevron(isExpanded: isExpanded)
/// ```
struct DisclosureChevron: View {
    let isExpanded: Bool

    var body: some View {
        Image(systemName: "chevron.right")
            .font(DSFont.iconSM)
            .foregroundStyle(DSColor.textMuted)
            .rotationEffect(.degrees(isExpanded ? 90 : 0))
            .animation(.easeInOut(duration: 0.15), value: isExpanded)
            .frame(width: DSLayout.chevronFrameWidth)
    }
}
