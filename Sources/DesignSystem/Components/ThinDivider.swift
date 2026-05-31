// MARK: - ThinDivider / VerticalDivider
// 1pt rectangle dividers for sidebar/panel separators.
// macOS 14+, Swift 5.10

import SwiftUI

/// 1pt horizontal divider.
///
/// Use instead of `Rectangle().fill(color).frame(height: 1)` — keeps the
/// design system consistent and lets us swap implementation later.
struct ThinDivider: View {

    var color: Color = DSColor.borderSubtle

    var body: some View {
        Rectangle()
            .fill(color)
            .frame(height: 1)
    }
}

/// 1pt vertical divider.
///
/// Use instead of `Rectangle().fill(color).frame(width: 1)`.
struct VerticalDivider: View {

    var color: Color = DSColor.borderDefault

    var body: some View {
        Rectangle()
            .fill(color)
            .frame(width: 1)
    }
}
