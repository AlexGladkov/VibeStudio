// MARK: - CostBadgeView
// Real-time agent cost/token display badge for the session toolbar.
// macOS 14+, Swift 5.10

import SwiftUI

/// Compact live badge showing accumulated agent session cost and token count.
///
/// States:
/// - **idle** (snapshot == nil): entirely hidden (`.opacity(0)` + zero width).
/// - **active**: shows `$0.02 · 8.5k tok` in a coloured pill.
/// - **unknown cost** (costUSD == nil): shows `$? · 8.5k tok`.
///
/// Color semantics:
/// - `textSecondary` — normal range
/// - `gitModified`   — token count crosses `warningThreshold`
/// - `indicatorError` — token count crosses `alertThreshold`
///
/// The badge reserves a fixed-size slot when active so the toolbar doesn't
/// jump when numbers change (E15 — long sums at $127 level still fit).
struct CostBadgeView: View {

    /// Cost/token snapshot to display. `nil` = hidden.
    let snapshot: SessionCostSnapshot?

    /// Token count at which the badge turns warning-yellow.
    var warningThreshold: Int = 50_000

    /// Token count at which the badge turns error-red.
    var alertThreshold: Int = 200_000

    var body: some View {
        if let snap = snapshot, snap.totalTokens > 0 {
            Text(badgeText(snap))
                .font(DSFont.smallButtonLabel.monospacedDigit())
                .foregroundStyle(badgeColor(snap))
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
                .padding(.horizontal, DSSpacing.xs)
                .padding(.vertical, DSSpacing.xxs)
                .background(
                    badgeColor(snap).opacity(0.12),
                    in: RoundedRectangle(cornerRadius: DSRadius.sm)
                )
                .frame(height: DSLayout.toolbarButtonHeight)
                .contentTransition(.numericText())
                .accessibilityLabel(accessibilityLabel(snap))
                .help(helpText(snap))
        }
    }

    // MARK: - Private: Text Formatting

    private func badgeText(_ snap: SessionCostSnapshot) -> String {
        let costStr: String
        if let cost = snap.costUSD {
            costStr = formatCost(cost)
        } else {
            costStr = "$?"
        }
        return "\(costStr) · \(formatTokens(snap.totalTokens))"
    }

    private func formatCost(_ usd: Double) -> String {
        if usd < 0.01 {
            return String(format: "$%.4f", usd)
        } else if usd < 10 {
            return String(format: "$%.2f", usd)
        } else {
            return String(format: "$%.1f", usd)
        }
    }

    private func formatTokens(_ n: Int) -> String {
        if n >= 1_000_000 {
            return String(format: "%.1fM tok", Double(n) / 1_000_000.0)
        } else if n >= 1_000 {
            return String(format: "%.1fk tok", Double(n) / 1_000.0)
        } else {
            return "\(n) tok"
        }
    }

    // MARK: - Private: Color

    private func badgeColor(_ snap: SessionCostSnapshot) -> Color {
        if snap.totalTokens >= alertThreshold {
            return DSColor.indicatorError
        } else if snap.totalTokens >= warningThreshold {
            return DSColor.gitModified
        } else {
            return DSColor.textSecondary
        }
    }

    // MARK: - Private: Accessibility

    private func accessibilityLabel(_ snap: SessionCostSnapshot) -> String {
        let costPart: String
        if let cost = snap.costUSD {
            costPart = String(format: "$%.4f", cost)
        } else {
            costPart = "unknown cost"
        }
        return "Session cost: \(costPart), \(snap.totalTokens) tokens total"
    }

    private func helpText(_ snap: SessionCostSnapshot) -> String {
        var parts: [String] = []
        parts.append("Prompt tokens: \(snap.promptTokens)")
        parts.append("Completion tokens: \(snap.completionTokens)")
        if let model = snap.model {
            parts.append("Model: \(model)")
        }
        if let cost = snap.costUSD {
            parts.append(String(format: "Estimated cost: $%.6f", cost))
        } else {
            parts.append("Cost: unknown model — add to pricing table")
        }
        parts.append("")
        parts.append(ModelPricingTable.disclaimer)
        return parts.joined(separator: "\n")
    }
}
