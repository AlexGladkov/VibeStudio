// MARK: - AheadBehindBadgeView
// Reusable ahead/behind badge for git branch indicators.
// macOS 14+, Swift 5.10

import SwiftUI

/// Shows ahead and/or behind counts for a git branch.
///
/// Replaces duplicated inline HStack with `spacing: 1/3`, arrow icons, `font size: 8/10`.
///
/// Usage:
/// ```swift
/// AheadBehindBadgeView(ahead: branch.ahead, behind: branch.behind)
/// ```
struct AheadBehindBadgeView: View {
    let ahead: Int
    let behind: Int

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            if ahead > 0 {
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "arrow.up")
                        .font(DSFont.iconXS)
                    Text("\(ahead)")
                        .font(DSFont.iconMD)
                }
                .foregroundStyle(DSColor.textSecondary)
            }
            if behind > 0 {
                HStack(spacing: DSSpacing.xxs) {
                    Image(systemName: "arrow.down")
                        .font(DSFont.iconXS)
                    Text("\(behind)")
                        .font(DSFont.iconMD)
                }
                .foregroundStyle(DSColor.textSecondary)
            }
        }
    }
}
