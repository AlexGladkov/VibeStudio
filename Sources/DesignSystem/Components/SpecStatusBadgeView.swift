// MARK: - SpecStatusBadgeView
// Unified pass/fail/unknown badge for a spec file.
// macOS 14+, Swift 5.10

import SwiftUI

/// Status badge for a single `SpecFile` row.
///
/// - `.passing` → green checkmark
/// - `.failing` → red xmark
/// - `.unknown` → optional questionmark (controlled by `showUnknown`)
///
/// Used by both `SpecsPanelView` (regular sidebar) and `CodeSpeakModeView`
/// (full-window mode) — replaces the duplicated `specStatusBadge` switches.
struct SpecStatusBadgeView: View {

    let status: SpecStatus
    /// Whether to render a "?" placeholder when status is `.unknown`.
    var showUnknown: Bool = false

    var body: some View {
        switch status {
        case .passing:
            Image(systemName: "checkmark")
                .font(DSFont.statusBadge)
                .foregroundStyle(status.color)
        case .failing:
            Image(systemName: "xmark")
                .font(DSFont.statusBadge)
                .foregroundStyle(status.color)
        case .unknown:
            if showUnknown {
                Image(systemName: "questionmark")
                    .font(DSFont.iconMD)
                    .foregroundStyle(DSColor.textMuted)
            } else {
                EmptyView()
            }
        }
    }
}
