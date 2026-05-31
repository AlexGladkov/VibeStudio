// MARK: - SpecStatus+Display
// Presentation helpers for SpecStatus (icon name + dot colour).
// macOS 14+, Swift 5.10

import SwiftUI

extension SpecStatus {

    /// SF Symbol name representing this status in the spec list.
    ///
    /// `nil` for `.unknown` — caller should omit the badge entirely.
    var iconName: String? {
        switch self {
        case .passing: return "checkmark"
        case .failing: return "xmark"
        case .unknown: return nil
        }
    }

    /// Colour used for the status dot and the badge icon.
    var color: Color {
        switch self {
        case .passing: return DSColor.gitAdded
        case .failing: return DSColor.gitDeleted
        case .unknown: return DSColor.indicatorIdle
        }
    }
}
