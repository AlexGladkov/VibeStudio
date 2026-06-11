// MARK: - MIMETypeMap
// Single lookup table for static file MIME types.
// macOS 14+, Swift 5.10

import Foundation

/// Single source of truth for static-file MIME type lookup used by the
/// embedded web UI server.
enum MIMETypeMap {

    /// Resolve a MIME type for the given file-name extension.
    /// Returns `application/octet-stream` for unknown extensions.
    static func forExtension(_ ext: String) -> String {
        switch ext.lowercased() {
        case "js":    return "application/javascript"
        case "css":   return "text/css"
        case "html":  return "text/html"
        case "json":  return "application/json"
        case "png":   return "image/png"
        case "svg":   return "image/svg+xml"
        case "ico":   return "image/x-icon"
        case "woff2": return "font/woff2"
        case "woff":  return "font/woff"
        default:      return "application/octet-stream"
        }
    }
}
