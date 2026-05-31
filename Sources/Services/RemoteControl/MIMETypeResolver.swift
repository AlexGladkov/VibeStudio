// MARK: - MIMETypeResolver
// ARCH-H2: Single source of truth for file extension → MIME type mapping
// used by both the static-file cache loader and the on-the-fly request
// router. Previously duplicated in HTTPRequestRouter.mimeType(for:) and
// RemoteControlServer.mimeTypeStatic(for:); divergence between the two
// could silently break content negotiation for new asset types.
//
// macOS 14+, Swift 5.10

import Foundation

/// Resolves the `Content-Type` header value for a file extension.
///
/// All Remote Control static assets (web UI, vendor bundles) go through
/// this resolver so that the bundle pre-loader and the live request handler
/// always agree on the MIME type.
enum MIMETypeResolver {
    /// Returns the MIME type for the given file extension.
    ///
    /// - Parameter ext: File extension without the leading dot (e.g. `"js"`).
    ///   Lookup is case-insensitive.
    /// - Returns: The matching MIME type, or `"application/octet-stream"`
    ///   when the extension is not registered.
    static func mimeType(for ext: String) -> String {
        Self.map[ext.lowercased()] ?? "application/octet-stream"
    }

    /// Extension → MIME type registry. Add new asset kinds here.
    private static let map: [String: String] = [
        "js": "application/javascript",
        "css": "text/css",
        "html": "text/html",
        "json": "application/json",
        "png": "image/png",
        "svg": "image/svg+xml",
        "ico": "image/x-icon",
        "woff2": "font/woff2",
        "woff": "font/woff"
    ]
}
