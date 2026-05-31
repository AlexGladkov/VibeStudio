// MARK: - BuildLineKind
// Classification for CodeSpeak build-output lines (drives colouring).
// macOS 14+, Swift 5.10

import Foundation
import SwiftUI

/// Semantic classification of a single line of CodeSpeak build output.
///
/// Used by `SpecBuildPanelView` and `CodeSpeakModeView` to colourise the
/// build log without each view re-implementing the same heuristic.
enum BuildLineKind {
    /// Default text — no special highlight.
    case info
    /// Warning line ("warn", etc.).
    case warning
    /// Success line ("pass", checkmark glyphs).
    case success
    /// Error line ("error", "fail", warning-triangle prefix).
    case error
}

extension String {

    /// Heuristic classification of this line for build-output colouring.
    ///
    /// Order matters: error > success > warning > info. This mirrors the
    /// original inline `lineColor` / `buildLineColor` helpers.
    var buildLineKind: BuildLineKind {
        let lower = self.lowercased()
        if lower.contains("error") || lower.contains("fail") || hasPrefix("\u{26A0}") {
            return .error
        }
        if lower.contains("pass") || lower.contains("\u{2713}") || lower.contains("\u{2714}") {
            return .success
        }
        if lower.contains("warn") {
            return .warning
        }
        return .info
    }
}

extension DSColor {

    /// Foreground colour for a single line of build output.
    ///
    /// - Parameter kind: Classification of the line.
    /// - Returns: Matching `DSColor` token.
    static func buildOutputColor(for kind: BuildLineKind) -> Color {
        switch kind {
        case .error:   return DSColor.gitDeleted
        case .success: return DSColor.gitAdded
        case .warning: return DSColor.gitModified
        case .info:    return DSColor.textPrimary
        }
    }
}
