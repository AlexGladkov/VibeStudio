// MARK: - AheadBehindBadgeView
// Reusable ahead/behind badge for git branch indicators.
// macOS 14+, Swift 5.10

import SwiftUI

/// Shows ahead and/or behind counts for a git branch.
///
/// Replaces duplicated inline HStack with `spacing: 1/3`, arrow icons, `font size: 8/10`.
///
/// Defaults match the most common call-site (git branch row, where ahead uses
/// the "added" green and behind uses the "deleted" red). Override the
/// `aheadColor` / `behindColor` parameters to render the badge in a neutral
/// tint when needed.
///
/// Usage:
/// ```swift
/// AheadBehindBadgeView(ahead: branch.ahead, behind: branch.behind)
/// ```
struct AheadBehindBadgeView: View {
    let ahead: Int
    let behind: Int

    /// Color applied to the up-arrow + count. Defaults to the semantic
    /// "added" git color (green) used by every existing call-site.
    var aheadColor: Color = DSColor.gitAdded

    /// Color applied to the down-arrow + count. Defaults to the semantic
    /// "deleted" git color (red) used by every existing call-site.
    var behindColor: Color = DSColor.gitDeleted

    /// Outer horizontal spacing between the ahead and behind groups.
    /// Defaults to `xxs` so the badge sits tight inside compact rows.
    var outerSpacing: CGFloat = DSSpacing.xxs

    var body: some View {
        HStack(spacing: outerSpacing) {
            if ahead > 0 {
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "arrow.up")
                        .font(DSFont.iconXS)
                    Text("\(ahead)")
                        .font(DSFont.iconMD)
                }
                .foregroundStyle(aheadColor)
            }
            if behind > 0 {
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "arrow.down")
                        .font(DSFont.iconXS)
                    Text("\(behind)")
                        .font(DSFont.iconMD)
                }
                .foregroundStyle(behindColor)
            }
        }
    }
}
