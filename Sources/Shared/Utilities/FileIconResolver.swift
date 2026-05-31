// MARK: - FileIconResolver
// SF Symbol + tint resolver for file URLs, by extension.
// macOS 14+, Swift 5.10

import SwiftUI

/// Resolves the SF Symbol name and tint color for a given file URL.
///
/// Mirrors the previous inline mapping used by `FileTreeView`, factored
/// out so other views (tabs, breadcrumbs, search results) can reuse the
/// same iconography without duplicating the switch.
public enum FileIconResolver {

    /// Icon descriptor for the given file URL.
    ///
    /// - Parameter url: The file URL to inspect; only `pathExtension` is read.
    /// - Returns: SF Symbol name and the tint color to apply.
    public static func icon(for url: URL) -> (name: String, color: Color) {
        let ext = url.pathExtension.lowercased()
        let name: String
        switch ext {
        case "swift":
            name = "swift"
        case "json", "yaml", "yml", "toml":
            name = "gearshape.fill"
        default:
            name = "doc.text.fill"
        }

        let color: Color
        switch ext {
        case "swift":
            color = DSColor.swiftOrange
        default:
            color = DSColor.textSecondary
        }

        return (name, color)
    }
}
