// MARK: - SpecStatus+Display
// Presentation helpers for SpecStatus (dot colour).
// macOS 14+, Swift 5.10

import SwiftUI

extension SpecStatus {

    /// Colour used for the status dot and the badge icon.
    var color: Color {
        switch self {
        case .passing: return DSColor.gitAdded
        case .failing: return DSColor.gitDeleted
        case .unknown: return DSColor.indicatorIdle
        }
    }
}
