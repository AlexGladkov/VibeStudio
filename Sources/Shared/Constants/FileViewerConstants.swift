// MARK: - FileViewerConstants
// Layout constants for the file viewer sheet.
// macOS 14+, Swift 5.10

import Foundation

/// Layout constants for the FileViewerSheet.
enum FileViewerConstants {
    /// Sheet width when viewing a single file.
    static let singleFileWidth: CGFloat = 600
    /// Sheet width when comparing two files.
    static let twoFileWidth: CGFloat = 1_100
    /// Sheet width when comparing three files.
    static let threeFileWidth: CGFloat = 1_280
    /// Sheet height.
    static let sheetHeight: CGFloat = 600
}
