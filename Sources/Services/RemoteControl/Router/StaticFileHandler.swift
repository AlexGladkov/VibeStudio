// MARK: - StaticFileHandler
// Serves the embedded RemoteControlWeb bundle (with on-disk fallback).
// macOS 14+, Swift 5.10

import Foundation

/// Resolves static-file requests for the Remote Control Web UI.
///
/// **Lookup order:**
/// 1. In-memory cache populated at server start.
/// 2. On-disk fallback under the app bundle (used by unit tests and during
///    development when the cache is empty).
///
/// Returns `nil` when the file is not found or the resolved path escapes the
/// bundle root (defensive path traversal guard).
struct StaticFileHandler {

    /// Pre-loaded files keyed by relative path (`index.html`, `vendor/xterm.js`).
    let cache: [String: (Data, String)]

    /// Resource bundle root for the on-disk fallback. `nil` when the bundle is
    /// unavailable (e.g. unit-test target).
    let bundleRoot: URL?

    init(cache: [String: (Data, String)], bundleRoot: URL?) {
        self.cache = cache
        self.bundleRoot = bundleRoot
    }

    /// Lookup result for a static-file request.
    enum Result {
        /// Found, body and content type ready to send.
        case found(data: Data, contentType: String)
        /// File not found.
        case notFound
        /// Resolved path escapes the bundle root.
        case forbidden
    }

    /// Resolve `fileName` (relative path, e.g. `index.html` or `vendor/xterm.js`)
    /// using `defaultContentType` for the disk fallback path. Cache entries
    /// retain their own content type recorded at preload time.
    func serve(fileName: String, defaultContentType: String) -> Result {
        if let (cachedData, cachedContentType) = cache[fileName] {
            return .found(data: cachedData, contentType: cachedContentType)
        }
        guard let baseURL = bundleRoot else { return .notFound }
        let resolved = baseURL.appendingPathComponent(fileName).standardized
        // Defensive guard: ensure path stays under the bundle root.
        guard resolved.path.hasPrefix(baseURL.standardized.path) else {
            return .forbidden
        }
        guard let data = try? Data(contentsOf: resolved) else { return .notFound }
        return .found(data: data, contentType: defaultContentType)
    }
}
