// MARK: - AppVersionBadge
// Unobtrusive running-build indicator for the titlebar (right of traffic lights).
// macOS 14+, Swift 5.10

import SwiftUI

/// Ghost-muted `vX.Y.Z (build)` label so it's always clear which build is
/// running, without competing with toolbar content. Rendered in the titlebar
/// leading area, just right of the macOS traffic-light buttons.
struct AppVersionBadge: View {

    var body: some View {
        // No `fixedSize` — call sites clamp the width (`.frame(maxWidth:)`) so the
        // badge never spills past the sidebar/content divider. Truncates tail-first
        // when the sidebar is dragged too narrow to fit the full string.
        Text("Build version: \(Bundle.main.appShortVersion)")
            .font(DSFont.badgeSmall)
            .foregroundStyle(DSColor.textGhost)
            .monospacedDigit()
            .lineLimit(1)
            .truncationMode(.tail)
            .help("Running build \(Bundle.main.appVersionDisplay)")
            .accessibilityLabel("Build version \(Bundle.main.appShortVersion)")
    }
}
