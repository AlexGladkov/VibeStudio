// MARK: - RemoteStaticFileCache
// ARCH-H7 + ARCH-H8: Encapsulates the in-memory static asset cache and
// the Bundle resource URL resolution. Created on MainActor at server
// start so that NIO event-loop threads never call `Bundle.main.url(...)`
// or do disk reads inside a request hot path.
//
// macOS 14+, Swift 5.10

import Foundation
import OSLog

/// Pre-loaded snapshot of the `RemoteControlWeb` bundle directory.
///
/// All `Bundle.main` access happens once during initialization on
/// `MainActor`; the cache itself is immutable and `Sendable` so it can be
/// captured by NIO closures without crossing actor boundaries.
struct RemoteStaticFileCache: Sendable {
    /// Cached static files keyed by relative path (e.g. `"index.html"`,
    /// `"vendor/xterm.js"`) → `(data, contentType)`.
    let files: [String: (Data, String)]

    /// Absolute URL of the `RemoteControlWeb` resource directory at the time
    /// of initialization. `nil` if the bundle is missing (e.g. unit tests).
    ///
    /// Captured once on MainActor so the NIO event loop never calls
    /// `Bundle.main.url(forResource:)` itself. Disk fallbacks in
    /// ``HTTPRequestRouter.serveStaticFile`` use this stored URL.
    let baseURL: URL?

    /// Empty cache for previews/tests that never serve files.
    static let empty = RemoteStaticFileCache(files: [:], baseURL: nil)

    // MARK: - Loading

    /// Pre-load all files from the `RemoteControlWeb` bundle directory.
    ///
    /// Must be called on MainActor (Bundle access). Returns an empty cache
    /// if the bundle directory is absent.
    @MainActor
    static func load() -> RemoteStaticFileCache {
        guard let baseURL = Bundle.main.url(forResource: "RemoteControlWeb", withExtension: nil) else {
            Logger.remoteControl.warning(
                "RemoteStaticFileCache: RemoteControlWeb bundle not found — static files will be served from disk"
            )
            return RemoteStaticFileCache(files: [:], baseURL: nil)
        }

        var cache: [String: (Data, String)] = [:]
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(
            at: baseURL,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else {
            return RemoteStaticFileCache(files: [:], baseURL: baseURL)
        }

        for case let fileURL as URL in enumerator {
            guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]))?.isRegularFile == true else {
                continue
            }
            let fullPath = fileURL.standardized.path
            let basePath = baseURL.standardized.path
            guard fullPath.hasPrefix(basePath) else { continue }
            var relativePath = String(fullPath.dropFirst(basePath.count))
            if relativePath.hasPrefix("/") { relativePath = String(relativePath.dropFirst()) }

            guard let data = try? Data(contentsOf: fileURL) else { continue }
            let ext = (relativePath as NSString).pathExtension
            cache[relativePath] = (data, MIMETypeResolver.mimeType(for: ext))
        }

        Logger.remoteControl.info(
            "RemoteStaticFileCache: pre-loaded \(cache.count) static files from RemoteControlWeb bundle"
        )
        return RemoteStaticFileCache(files: cache, baseURL: baseURL)
    }
}
